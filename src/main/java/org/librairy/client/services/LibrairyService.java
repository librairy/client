package org.librairy.client.services;

import com.google.common.base.Strings;
import lombok.Getter;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.librairy.client.dao.DomainsDao;
import org.librairy.client.dao.ItemsDao;
import org.librairy.client.dao.PartsDao;
import org.librairy.client.exceptions.AlreadyExistException;
import org.librairy.client.exceptions.InvalidCredentialsError;
import org.librairy.client.exceptions.ModelError;
import org.librairy.client.exceptions.StorageError;
import org.librairy.client.model.*;
import org.librairy.client.topics.Estimator;
import org.librairy.client.topics.Inferencer;
import org.librairy.client.topics.LDACmdOption;
import org.librairy.client.topics.Model;
import org.librairy.metrics.data.Ranking;
import org.librairy.metrics.distance.RankingSimilarityMetric;
import org.librairy.metrics.similarity.JensenShannonSimilarity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * @author Badenes Olmedo, Carlos <cbadenes@fi.upm.es>
 */
public class LibrairyService {

    private static final Logger LOG = LoggerFactory.getLogger(LibrairyService.class);

    @Getter
    private final DomainsDao domainsDao;
    @Getter
    private final ItemsDao  itemsDao;
    @Getter
    private final PartsDao partsDao;

    private final RestService restService;


    public LibrairyService(RestService restService){
        this.restService    = restService;
        this.domainsDao     = new DomainsDao(restService);
        this.itemsDao       = new ItemsDao(restService);
        this.partsDao       = new PartsDao(restService);

        domainsDao.setItemsDao(itemsDao);
    }

    public void addFolder(String folderPath, List<String> domains, Optional<Map<String,String>> parameters ) throws IOException {
        Instant startModel  = Instant.now();
        AtomicInteger counter = new AtomicInteger();


        Files.list(Paths.get(folderPath)).forEach(paper -> {
            File file = paper.toFile();
            try {
                counter.incrementAndGet();
                String name     = StringUtils.substringBeforeLast(file.getName(),".");
                String content  = null;
                switch(StringUtils.substringAfterLast(file.getName().toLowerCase(),".")){
                    case "pdf": content  = PdfService.toString(file);
                    case "txt" : content  = Files.lines(file.toPath()).collect(Collectors.joining());
                    default: LOG.warn("File: " + file.getAbsolutePath() + " not supported");
                }

                if (content != null) newItem(new DataItem(name,content, DataItem.LANGUAGE.EN),domains);
            } catch (IOException e) {
                LOG.error("No file found: " + file.getAbsolutePath(),e);
            } catch (StorageError e) {
                LOG.error("Item not added: " + file.getAbsolutePath(),e);
            } catch (Exception e){
                LOG.error("Error handling file: " + file.getAbsolutePath(),e);
            }
        });

        Instant endModel    = Instant.now();
        LOG.info( counter.get() +" documents retrieved in: "
                + ChronoUnit.HOURS.between(startModel,endModel) + "hours "
                + ChronoUnit.MINUTES.between(startModel,endModel)%60 + "min "
                + (ChronoUnit.SECONDS.between(startModel,endModel)%3600) + "secs");

        if (counter.get() > 0){
            // update topics in domain
            domains.forEach( domain -> {
                try {
                    domainsDao.updateTopics(domain);
                } catch (IOException e) {
                    LOG.warn("Domain not updated: " + domain + ": " +  e.getMessage());
                }
            });

        }

    }


    private String newItem(DataItem dataItem, List<String> domains) throws StorageError, InvalidCredentialsError, AlreadyExistException {

        if (dataItem.isEmpty()){
            throw new StorageError("DataItem without name or content");
        }

        String nameCandidate = (Strings.isNullOrEmpty(dataItem.getName()))? StringUtils.substringBefore(dataItem.getContent(),"\n") : dataItem.getName();
        String itemId = itemsDao.save(nameCandidate,dataItem.getLanguage().name(),dataItem.getContent());

        if (!domains.isEmpty()){

            for(String domainId: domains){

                try {
                    String id = domainsDao.save(domainId, "");
                    domainsDao.addItem(id,itemId);
                } catch (UnsupportedEncodingException e) {
                    throw new StorageError("Invalid domain name: " + domainId, e);
                }
            }
        }
        LOG.info("Saved: " + restService.uriOf("item",itemId));
        return itemId;
    }


    public DataModel newModel(DataModel.ALGORITHM algorithm, String domain, Optional<List<String>> stopwords, Optional<Integer> iterations, Optional<Integer> numTopics, Optional<Double> alpha, Optional<Double> beta, Optional<Integer> wordsPerTopic, Optional<Integer> maxSize) throws ModelError {

        String domainId;
        try {
            domainId = URLEncoder.encode(domain,"UTF-8");
            if(!domainsDao.exists(domainId)) throw new ModelError("Domain does not exist: " + domainId);

        } catch (UnsupportedEncodingException e) {
            throw new ModelError("Invalid domain name: " + domain);
        }

        try {

            if (stopwords.isPresent())
            {
                LOG.info("Using stop-words " + stopwords.get());
            }

            //create a temp file
            File csvFile = File.createTempFile("dataset", ".csv");

            csvFile.mkdirs();
            csvFile.createNewFile();
            FileWriter writer = new FileWriter(csvFile);

            AtomicInteger counter = new AtomicInteger();
            boolean completed = false;

            Optional<Integer> size  = Optional.of(100);
            Optional<String> offset = Optional.empty();

            Integer maxElements = maxSize.isPresent()? maxSize.get() : 100;
            List<String> uris = new ArrayList<>();
            while(!completed && (counter.get() <= maxElements)){
                List<Item> items = domainsDao.getItems(domainId, false, size, offset);

                completed = items.size() < size.get();
                offset = Optional.of(items.get(items.size()-1).getId());

                for(Item item: items){
                    if (counter.incrementAndGet() > maxElements) break;
                    String content = itemsDao.getAnnotation(item.getId(),"lemma");
                    uris.add(item.getUri());
                    String finalContent = content;
                    if (stopwords.isPresent()){
                        finalContent = Arrays.stream(content.split(" ")).filter(token -> !stopwords.get().contains(token)).collect(Collectors.joining(" "));
                    }
                    writer.write(finalContent+"\n");
                }
            }

            writer.close();


            LOG.debug("created csv file: " + csvFile.getAbsolutePath());

            File folder = Paths.get("output", "models","lda",domainId).toFile();
            folder.mkdirs();

            File gzFile = new File(folder.getAbsolutePath()+File.separator+"dataset.csv.gz");
            gzFile.createNewFile();

            FileService.gzipIt(csvFile.getAbsolutePath(), gzFile.getAbsolutePath());


            Integer iterationsValue = iterations.isPresent()? iterations.get() : 1000;
            Double alphaValue   = alpha.isPresent()? alpha.get() : 0.1;
            Double betaValue    = beta.isPresent()? beta.get() : 0.01;
            Integer topicsValue = numTopics.isPresent()? numTopics.get() : Double.valueOf(2*Math.sqrt(counter.get()/2)).intValue();
            Integer wordsValue  = wordsPerTopic.isPresent()? wordsPerTopic.get() : 10;

            LOG.info("Ready to train a " + algorithm + " model with " + topicsValue + " topics (alpha="+alphaValue+"/beta="+betaValue+") in " + iterationsValue + " iterations" );
            String[] args = new String[]{
                    "-est",
                    "-alpha",String.valueOf(alphaValue),
                    "-beta",String.valueOf(betaValue),
                    "-ntopics",String.valueOf(topicsValue),
                    "-niters",String.valueOf(iterationsValue),
                    "-twords",String.valueOf(wordsValue),
                    "-dir",folder.getAbsolutePath(),
                    "-dfile",gzFile.getName(),
                    "-model","model"
            };


            LDACmdOption option = new LDACmdOption();
            CmdLineParser parser = new CmdLineParser(option);
            parser.parseArgument(args);
            Estimator estimator = new Estimator(option);
            estimator.estimate();

            DataModel dataModel = estimator.getTrnModel().getDataModel();

            dataModel.getTopics().forEach( topic -> {
                LOG.info("Topic " + topic.getId());
                topic.getWords().forEach( word -> {
                    LOG.info("\t - " + word.getValue() + " \t:" + word.getScore());
                });
            });


            // update shapes
            List<Shape> newShapes = dataModel.getShapes().stream().sorted((a, b) -> Integer.valueOf(a.getUri()).compareTo(Integer.valueOf(b.getUri()))).map(shape -> {
                String uri = uris.get(Integer.valueOf(shape.getUri()));
                Shape newShape = new Shape();
                newShape.setType(StringUtils.substringAfterLast(uri,"/"));
                newShape.setUri(uri);
                newShape.setVector(shape.getVector());
                return newShape;
            }).collect(Collectors.toList());

            dataModel.setShapes(newShapes);


            BufferedWriter uriWriter = FileService.writer(Paths.get("output", "models", "lda", domainId, "model.uris.txt").toFile().getAbsolutePath());
            for(String uri: uris){
                uriWriter.write(uri+"\n");
            }
            uriWriter.close();

            return dataModel;

        } catch (IOException e) {
            LOG.error("Error",e);
            throw new ModelError("Error creating temporal files",e);
        } catch (CmdLineException e) {
            LOG.error("Error",e);
            throw new ModelError("Error creating temporal files",e);
        }


    }



    public void inference(String domainId, File text, Optional<Integer> wordsPerTopic, Optional<Integer> iterations) throws ModelError {

        LOG.info("loading existing model for domain: " + domainId + " ..");

        File folder = Paths.get("output", "models","lda",domainId).toFile();

        if (!folder.exists()) {
            LOG.error("Model not found! Try to train a LDA model before to inference");
            return;
        }

        String numWords         = wordsPerTopic.isPresent()? String.valueOf(wordsPerTopic.get()) : "10";
        String numIterations    = iterations.isPresent()? String.valueOf(iterations.get()) : "20";

        String[] args = new String[]{
                "-inf",
                "-dir",folder.getAbsolutePath(),
                "-model","model",
                "-twords",numWords,
                "-niters",numIterations,
                "-dfile",text.getAbsolutePath()
        };


        try {
            LDACmdOption option = new LDACmdOption();
            CmdLineParser parser = new CmdLineParser(option);
            parser.parseArgument(args);
            LOG.info("inference topic distributions for document: " + text.getAbsolutePath() + " ..");
            Inferencer inferencer = new Inferencer(option);
            Model model = inferencer.inference();

            LOG.info("Topic Distributions: ");
            model.getDataModel().getShapes().forEach(shape -> {
                int index = 0;
                for (Double score : shape.getVector()){
                    LOG.info("- Topic " + index++ + ": \t" + score);
                }
            });

            // move output files

            File outputFolder = Paths.get("output", "inferences","lda",domainId).toFile();
            if (!outputFolder.exists()) outputFolder.mkdirs();

            Paths.get(text.getParent(),text.getName()+".model.documents.txt");

            Files.move(Paths.get(text.getName()+".model.documents.txt"),Paths.get("output", "inferences","lda",domainId,text.getName()+".model.documents.txt"), StandardCopyOption.REPLACE_EXISTING);
            Files.move(Paths.get(text.getName()+".model.parameters.txt"),Paths.get("output", "inferences","lda",domainId,text.getName()+".model.parameters.txt"), StandardCopyOption.REPLACE_EXISTING);
            Files.move(Paths.get(text.getName()+".model.topics.txt"),Paths.get("output", "inferences","lda",domainId,text.getName()+".model.topics.txt"), StandardCopyOption.REPLACE_EXISTING);
            Files.move(Paths.get(text.getName()+".model.vocabulary.txt"),Paths.get("output", "inferences","lda",domainId,text.getName()+".model.vocabulary.txt"), StandardCopyOption.REPLACE_EXISTING);
            Files.move(Paths.get(text.getName()+".model.words.txt"),Paths.get("output", "inferences","lda",domainId,text.getName()+".model.words.txt"), StandardCopyOption.REPLACE_EXISTING);

        } catch (CmdLineException e) {
            LOG.error("Error",e);
            throw new ModelError("Error creating temporal files",e);
        } catch (FileNotFoundException e) {
            LOG.error("File not found error: " + text.getAbsolutePath());
        } catch (IOException e) {
            LOG.error("Error",e);
            throw new ModelError("Error creating temporal files",e);
        }


    }


    public void compare(String domain1, String domain2, RankingSimilarityMetric metric, Optional<Integer> numWords) throws ModelError {


        try {
            File folder = Paths.get("output", "similarities", "topics", domain1+"-"+domain2).toFile();
            folder.mkdirs();


            List<Topic> topics1 = domainsDao.getTopics(domain1, numWords);
            writeTopics(topics1, new File(folder,"topics."+domain1+".txt"));

            List<Topic> topics2 = domainsDao.getTopics(domain2, numWords);
            writeTopics(topics2, new File(folder,"topics."+domain2+".txt"));



            File outputFile = new File(folder, "similarities.csv");

            BufferedWriter writer = FileService.writer(outputFile.getAbsolutePath());

            // Cartesian product
            LOG.info("Comparing all topic distributions ..");
            for(Topic t1 : topics1){

                for(Topic t2 : topics2){

                    Double score = compare(t1,t2,metric);

                    writer.write(domain1+"."+t1.getId()+","+domain2+"."+t2.getId()+","+score+"\n");
                }

            }
            writer.close();
            LOG.info("Similarities saved at " + outputFile.getAbsolutePath());
        } catch (IOException e) {
            LOG.error("Error",e);
            throw new ModelError("Error creating output file",e);
        }


    }


    private Double compare(Topic t1, Topic t2, RankingSimilarityMetric metric){

        Ranking r1 = new Ranking();

        for(Word wordDescription : t1.getWords()){
            r1.add(wordDescription.getValue(),wordDescription.getScore());
        }

        Ranking r2 = new Ranking();

        for(Word wordDescription : t2.getWords()){
            r2.add(wordDescription.getValue(),wordDescription.getScore());
        }


        return metric.calculate(r1,r2);

    }

    private void writeTopics(List<Topic> topics, File file) throws IOException {
        BufferedWriter writer = FileService.writer(file.getAbsolutePath());
        for(Topic topic : topics){
            writer.write("- Topic " + topic.getId()+" :\n");
            for (Word wordDescription : topic.getWords()){
                writer.write("\t - " + wordDescription.getValue() + " \t: " + wordDescription.getScore()+"\n");
            }
        }
        writer.close();
    }

    public Space similarities(String domain1, Optional<Double> minScore) throws ModelError {


        try {
            File folder = Paths.get("output", "similarities", "documents", domain1).toFile();
            folder.mkdirs();

            Space space = new Space();

            Optional<Integer> size = Optional.of(100);
            Optional<String> offset = Optional.empty();
            Boolean completed = false;

            ConcurrentLinkedQueue<DataShape> shapes = new ConcurrentLinkedQueue<>();

            LOG.info("Getting vectorial representations of documents in domain: " + domain1 + " ...");
            AtomicInteger counter = new AtomicInteger();
            while(!completed){

                List<Item> partialItems = domainsDao.getItems(domain1, false, size, offset);

                partialItems.parallelStream().forEach(item -> {

                    LOG.info("Item " + counter.incrementAndGet() + " loaded");
                    space.add(item);
                    DataShape shape = new DataShape();
                    shape.setUri(item.getUri());

                    List<Topic> topics = null;
                    try {
                        topics = domainsDao.getTopics(domain1, item.getId(), Optional.of(0));
                        List<Double> vector = topics.stream().sorted((a, b) -> a.getId().compareTo(b.getId())).map(t -> t.getScore()).collect(Collectors.toList());

                        shape.setVector(vector);
                        shapes.add(shape);

                    } catch (InvalidCredentialsError e) {
                        LOG.warn("error",e);
                    }

                });

                completed = partialItems.size() < size.get();

                if(!completed) offset = Optional.of(partialItems.get(partialItems.size()-1).getId());
            }


            File outputFile = new File(folder,"similarities.csv");
            BufferedWriter writer = FileService.writer(outputFile.getAbsolutePath());

            // Cartesian product
            LOG.info("Comparing all topic distributions ..");

            for(DataShape s1 : shapes){
                for(DataShape s2 : shapes){

                    if (s1.getUri().equalsIgnoreCase(s2.getUri())) continue;

                    double score = JensenShannonSimilarity.apply(s1.getVectorArray(), s2.getVectorArray());

                    if (minScore.isPresent() && score > minScore.get()){
                        writer.write(s1.getUri()+","+s2.getUri()+","+score+"\n");
                        space.add(new DataSimilarity(s1.getUri(),s2.getUri(), score));
                    }else if (!minScore.isPresent()){
                        writer.write(s1.getUri()+","+s2.getUri()+","+score+"\n");
                        space.add(new DataSimilarity(s1.getUri(),s2.getUri(), score));
                    }
                }
            }


            writer.close();
            LOG.info("Similarities saved at " + outputFile.getAbsolutePath());
            return space;
        } catch (IOException e) {
            LOG.error("Error",e);
            throw new ModelError("Error creating output file",e);
        }


    }




}
