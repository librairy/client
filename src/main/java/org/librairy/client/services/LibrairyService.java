package org.librairy.client.services;

import com.google.common.base.Strings;
import com.google.common.escape.Escaper;
import com.google.common.escape.Escapers;
import org.apache.commons.lang.StringUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.librairy.boot.model.Event;
import org.librairy.boot.model.domain.resources.*;
import org.librairy.boot.model.modules.EventBus;
import org.librairy.boot.model.modules.RoutingKey;
import org.librairy.boot.storage.dao.*;
import org.librairy.boot.storage.exception.DataNotFound;
import org.librairy.boot.storage.generator.URIGenerator;
import org.librairy.client.exceptions.ModelError;
import org.librairy.client.exceptions.StorageError;
import org.librairy.client.model.*;
import org.librairy.client.topics.Estimator;
import org.librairy.client.topics.Inferencer;
import org.librairy.client.topics.LDA;
import org.librairy.client.topics.LDACmdOption;
import org.librairy.metrics.data.Ranking;
import org.librairy.metrics.distance.*;
import org.librairy.metrics.similarity.JensenShannonSimilarity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.io.*;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

/**
 * @author Badenes Olmedo, Carlos <cbadenes@fi.upm.es>
 */
public class LibrairyService {

    private static final Logger LOG = LoggerFactory.getLogger(LibrairyService.class);

    private final ItemsDao itemsDao;
    private final DomainsDao domainsDao;
    private final AnnotationsDao annotationsDao;
    private final EventBus eventBus;
    private final ParametersDao parametersDao;
    private final TopicsDao topicsDao;

    public LibrairyService(ApplicationContext context){
        this.itemsDao       = context.getBean(ItemsDao.class);
        this.domainsDao     = context.getBean(DomainsDao.class);
        this.annotationsDao = context.getBean(AnnotationsDao.class);
        this.eventBus       = context.getBean(EventBus.class);
        this.parametersDao  = context.getBean(ParametersDao.class);
        this.topicsDao      = context.getBean(TopicsDao.class);
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
                String domainUri = URIGenerator.fromId(Resource.Type.DOMAIN, domain);
                Resource resource = new Resource();
                resource.setUri(domainUri);

                if (parameters.isPresent()){
                    parameters.get().entrySet().forEach(entry -> parametersDao.saveOrUpdate(domainUri,entry.getKey(),entry.getValue()));
                }

                this.eventBus.post(Event.from(resource),RoutingKey.of("domain.pending"));
            });

        }

    }


    private String newItem(DataItem dataItem, List<String> domains) throws StorageError {

        if (dataItem.isEmpty()){
            throw new StorageError("DataItem without name or content");
        }

        org.librairy.boot.model.domain.resources.Item internalItem = Resource.newItem(dataItem.getContent());
        String nameCandidate = StringUtils.substringBefore(dataItem.getContent(),"\n");
        internalItem.setDescription(nameCandidate.length()<100?nameCandidate:dataItem.getName());
        internalItem.setLanguage(dataItem.getLanguage().name().toLowerCase());

        if (!itemsDao.save(internalItem) && !itemsDao.exists(internalItem.getUri())) throw new StorageError("DataItem not saved");


        if (!domains.isEmpty()){

            for(String domainId: domains){

                String domainUri = null;
                try {
                    domainUri = URIGenerator.fromId(Resource.Type.DOMAIN, URLEncoder.encode(domainId,"UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    throw new StorageError("Invalid domain name: " + domainId, e);
                }

                if (!domainsDao.exists(domainUri)){
                    Domain domain = Resource.newDomain(domainId);
                    domain.setUri(domainUri);
                    domainsDao.save(domain);
                }

                if (!domainsDao.addItem(domainUri, internalItem.getUri())) throw new StorageError("DataItem not added to domain: " + domainId);
            }
        }
        LOG.info("Saved: " + internalItem.getUri());
        return internalItem.getUri();
    }


    public DataModel newModel(DataModel.ALGORITHM algorithm, String domain, Optional<List<String>> stopwords, Optional<Integer> iterations, Optional<Integer> numTopics, Optional<Double> alpha, Optional<Double> beta, Optional<Integer> wordsPerTopic, Optional<Integer> maxSize) throws ModelError {

        String domainId;
        String domainUri;
        try {
            domainId = URLEncoder.encode(domain,"UTF-8");
            domainUri = URIGenerator.fromId(Resource.Type.DOMAIN, domainId);
            if(!domainsDao.exists(domainUri)) throw new ModelError("Domain does not exist: " + domainUri);

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
            Integer size = 100;
            boolean completed = false;
            Optional<String> offset = Optional.empty();

            Integer maxElements = maxSize.isPresent()? maxSize.get() : 100;
            List<String> uris = new ArrayList<>();
            while(!completed && (counter.get() <= maxElements)){
                List<Item> items = domainsDao.listItems(domainUri, size, offset, false);

                completed = items.size() < size;
                offset = Optional.of(items.get(items.size()-1).getUri());

                for(Item item: items){
                    if (counter.incrementAndGet() > maxElements) break;
                    String content;
                    uris.add(item.getUri());
                    try {
                        List<Annotation> annotations = annotationsDao.getByResource(item.getUri(), Optional.of(AnnotationFilter.byType("lemma").build()));
                        if (annotations.isEmpty()) throw new DataNotFound("annotation is empty");
                        content = annotations.get(0).getValue().get("content");
                    } catch (DataNotFound dataNotFound) {
                        LOG.warn("No annotation found for item: " + item.getUri());
                        Item dataItem = itemsDao.get(item.getUri(), true).get().asItem();
                        content = TextService.escape(dataItem.getContent());
                    }
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
                newShape.setType(URIGenerator.retrieveId(uri));
                newShape.setUri(uri);
                newShape.setVector(shape.getVector());
                return newShape;
            }).collect(Collectors.toList());

            dataModel.setShapes(newShapes);

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
            inferencer.inference();
            LOG.info("topic distributions created");
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


            List<TopicDescription> topics1 = topicsDao.get(domain1, numWords);
            writeTopics(topics1, new File(folder,"topics."+domain1+".txt"));

            List<TopicDescription> topics2 = topicsDao.get(domain2, numWords);
            writeTopics(topics2, new File(folder,"topics."+domain2+".txt"));



            File outputFile = new File(folder, "similarities.csv");

            BufferedWriter writer = FileService.writer(outputFile.getAbsolutePath());

            // Cartesian product
            LOG.info("Comparing all topic distributions ..");
            for(TopicDescription t1 : topics1){

                for(TopicDescription t2 : topics2){

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


    private Double compare(TopicDescription t1, TopicDescription t2, RankingSimilarityMetric metric){

        Ranking r1 = new Ranking();

        for(WordDescription wordDescription : t1.getWords()){
            r1.add(wordDescription.getValue(),wordDescription.getScore());
        }

        Ranking r2 = new Ranking();

        for(WordDescription wordDescription : t2.getWords()){
            r2.add(wordDescription.getValue(),wordDescription.getScore());
        }


        return metric.calculate(r1,r2);

    }

    private void writeTopics(List<TopicDescription> topics, File file) throws IOException {
        BufferedWriter writer = FileService.writer(file.getAbsolutePath());
        for(TopicDescription topic : topics){
            writer.write("- Topic " + topic.getId()+" :\n");
            for (WordDescription wordDescription : topic.getWords()){
                writer.write("\t - " + wordDescription.getValue() + " \t: " + wordDescription.getScore()+"\n");
            }
        }
        writer.close();
    }

    public List<DataSimilarity> similarities(String domain1, Optional<Double> minScore) throws ModelError {


        try {
            File folder = Paths.get("output", "similarities", "documents", domain1).toFile();
            folder.mkdirs();


            String domainUri = URIGenerator.fromId(Resource.Type.DOMAIN, domain1);
            Integer size = 100;
            Optional<String> offset = Optional.empty();
            Boolean completed = false;

            List<DataShape> shapes = new ArrayList<>();

            LOG.info("Getting vectorial representations of documents in domain: " + domain1 + " ...");
            while(!completed){

                List<Item> partialItems = domainsDao.listItems(domainUri, size, offset, false);

                for(Item item: partialItems){
                    DataShape shape = new DataShape();
                    shape.setUri(item.getUri());

                    List<Double> vector = topicsDao.getShapeOf(item.getUri(), domain1);
                    shape.setVector(vector);
                    shapes.add(shape);

                }

                completed = partialItems.size() < size;

                if(!completed) offset = Optional.of(partialItems.get(partialItems.size()-1).getUri());
            }


            File outputFile = new File(folder,"similarities.csv");
            BufferedWriter writer = FileService.writer(outputFile.getAbsolutePath());

            // Cartesian product
            LOG.info("Comparing all topic distributions ..");

            List<DataSimilarity> similarities = new ArrayList<>();
            for(DataShape s1 : shapes){
                for(DataShape s2 : shapes){

                    if (s1.getUri().equalsIgnoreCase(s2.getUri())) continue;

                    double score = JensenShannonSimilarity.apply(s1.getVectorArray(), s2.getVectorArray());

                    if (minScore.isPresent() && score > minScore.get()){
                        writer.write(s1.getUri()+","+s2.getUri()+","+score+"\n");
                        similarities.add(new DataSimilarity(s1.getUri(),s2.getUri(), score));
                    }else if (!minScore.isPresent()){
                        writer.write(s1.getUri()+","+s2.getUri()+","+score+"\n");
                        similarities.add(new DataSimilarity(s1.getUri(),s2.getUri(), score));
                    }
                }
            }


            writer.close();
            LOG.info("Similarities saved at " + outputFile.getAbsolutePath());
            return similarities;
        } catch (IOException e) {
            LOG.error("Error",e);
            throw new ModelError("Error creating output file",e);
        }


    }

    public List<Resource> listItems(Optional<String> domain, Optional<Integer> maxNumber, Optional<String> offset){

        List<Resource> items = new ArrayList<>();
        if (domain.isPresent()){

            String domainUri   = URIGenerator.fromId(Resource.Type.DOMAIN, domain.get());
            boolean completed = false;
            Integer size = 100;
            AtomicInteger counter = new AtomicInteger();
            Optional<String> lastItem = offset;
            while(!completed){

                List<Item> partialItems = domainsDao.listItems(domainUri, size, lastItem, false);

                for(Item item: partialItems){
                    if (maxNumber.isPresent() && counter.incrementAndGet() > maxNumber.get()) break;
                    items.add(item);
                }

                completed = (partialItems.size() < size) || (maxNumber.isPresent() && counter.get() > maxNumber.get());

                if (!completed) lastItem = Optional.of(partialItems.get(partialItems.size()-1).getUri());

            }

        }

        return items;
    }



}
