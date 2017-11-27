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
import org.librairy.boot.model.domain.resources.Annotation;
import org.librairy.boot.model.domain.resources.Domain;
import org.librairy.boot.model.domain.resources.Item;
import org.librairy.boot.model.domain.resources.Resource;
import org.librairy.boot.model.modules.EventBus;
import org.librairy.boot.model.modules.RoutingKey;
import org.librairy.boot.storage.dao.*;
import org.librairy.boot.storage.exception.DataNotFound;
import org.librairy.boot.storage.generator.URIGenerator;
import org.librairy.client.exceptions.ModelError;
import org.librairy.client.exceptions.StorageError;
import org.librairy.client.model.DataItem;
import org.librairy.client.model.DataModel;
import org.librairy.client.topics.Estimator;
import org.librairy.client.topics.Inferencer;
import org.librairy.client.topics.LDA;
import org.librairy.client.topics.LDACmdOption;
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

    public LibrairyService(ApplicationContext context){
        this.itemsDao       = context.getBean(ItemsDao.class);
        this.domainsDao     = context.getBean(DomainsDao.class);
        this.annotationsDao = context.getBean(AnnotationsDao.class);
        this.eventBus       = context.getBean(EventBus.class);
        this.parametersDao = context.getBean(ParametersDao.class);
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


    public void newModel(DataModel.ALGORITHM algorithm, String domain, Optional<List<String>> stopwords, Optional<Integer> iterations, Optional<Integer> numTopics, Optional<Double> alpha, Optional<Double> beta, Optional<Integer> wordsPerTopic) throws ModelError {

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

            while(!completed){
                List<Item> items = domainsDao.listItems(domainUri, size, offset, false);

                completed = items.size() < size;
                offset = Optional.of(items.get(items.size()-1).getUri());

                for(Item item: items){
                    counter.incrementAndGet();
                    String content;
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

}
