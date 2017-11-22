package org.librairy.client.services;

import com.google.common.escape.Escaper;
import com.google.common.escape.Escapers;
import org.apache.commons.lang.StringUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.librairy.boot.model.domain.resources.Annotation;
import org.librairy.boot.model.domain.resources.Domain;
import org.librairy.boot.model.domain.resources.Item;
import org.librairy.boot.model.domain.resources.Resource;
import org.librairy.boot.storage.dao.AnnotationFilter;
import org.librairy.boot.storage.dao.AnnotationsDao;
import org.librairy.boot.storage.dao.DomainsDao;
import org.librairy.boot.storage.dao.ItemsDao;
import org.librairy.boot.storage.exception.DataNotFound;
import org.librairy.boot.storage.generator.URIGenerator;
import org.librairy.client.exceptions.ModelError;
import org.librairy.client.exceptions.StorageError;
import org.librairy.client.model.DataItem;
import org.librairy.client.model.DataModel;
import org.librairy.client.topics.LDA;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.io.*;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;

/**
 * @author Badenes Olmedo, Carlos <cbadenes@fi.upm.es>
 */
public class LibrairyService {

    private static final Logger LOG = LoggerFactory.getLogger(LibrairyService.class);

    private final ItemsDao itemsDao;
    private final DomainsDao domainsDao;
    private final AnnotationsDao annotationsDao;

    public LibrairyService(ApplicationContext context){
        this.itemsDao       = context.getBean(ItemsDao.class);
        this.domainsDao     = context.getBean(DomainsDao.class);
        this.annotationsDao = context.getBean(AnnotationsDao.class);
    }

    public void addFolder(String folderPath, List<String> domains ) throws IOException {
        Files.list(Paths.get(folderPath)).forEach(paper -> {
            File file = paper.toFile();
            try {
                String name     = StringUtils.substringBeforeLast(file.getName(),".");
                String content  = PdfService.toString(file);
                newItem(new DataItem(name,content, DataItem.LANGUAGE.EN),domains);
            } catch (IOException e) {
                LOG.error("No file found: " + file.getAbsolutePath(),e);
            } catch (StorageError e) {
                LOG.error("Item not added: " + file.getAbsolutePath(),e);
            }
        });
    }


    public String newItem(DataItem dataItem, List<String> domains) throws StorageError {

        if (dataItem.isEmpty()){
            throw new StorageError("DataItem without name or content");
        }

        String itemUri;
        try {
            itemUri = URIGenerator.fromId(Resource.Type.ITEM, URLEncoder.encode(dataItem.getName(),"UTF-8"));
        } catch (UnsupportedEncodingException e) {
            throw new StorageError("Invalid item name: " + dataItem.getName(), e);
        }

        org.librairy.boot.model.domain.resources.Item internalItem = Resource.newItem(dataItem.getContent());
        internalItem.setDescription(dataItem.getName());
        internalItem.setLanguage(dataItem.getLanguage().name().toLowerCase());
        internalItem.setUri(itemUri);

        if (!itemsDao.save(internalItem)) throw new StorageError("DataItem not saved");


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
        return itemUri;
    }

    public void deleteItem(String itemName) throws StorageError {

        String itemUri;
        try {
            itemUri = URIGenerator.fromId(Resource.Type.ITEM, URLEncoder.encode(itemName,"UTF-8"));
        } catch (UnsupportedEncodingException e) {
            throw new StorageError("Invalid name: " + itemName, e);
        }

        if (!itemsDao.delete(itemUri)) throw new StorageError("DataItem not deleted");
        LOG.info("Deleted: " + itemUri);
    }

    public void deleteDomain(String domain) throws StorageError {
        String domainUri = null;
        try {
            domainUri = URIGenerator.fromId(Resource.Type.DOMAIN, URLEncoder.encode(domain,"UTF-8"));
        } catch (UnsupportedEncodingException e) {
            throw new StorageError("Invalid domain name: " + domain, e);
        }
        domainsDao.delete(domainUri);
        LOG.info("Deleted: " + domainUri);
    }

    public void deleteAll(){
        itemsDao.deleteAll();
        LOG.info("Deleted All from librAIry");
    }


    public void newModel(DataModel.ALGORITHM algorithm, String domain, Optional<Integer> iterations, Optional<Integer> numTopics, Optional<Double> alpha, Optional<Double> beta, Optional<Integer> wordsPerTopic) throws ModelError {

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
                    writer.write(content+"\n");
                }
            }

            writer.close();


            LOG.debug("created csv file: " + csvFile.getAbsolutePath());

            File folder = Paths.get("output", "models","lda",domainId).toFile();
            folder.mkdirs();

            File gzFile = new File(folder.getAbsolutePath()+File.separator+"dataset.csv.gz");
            gzFile.createNewFile();

            gzipIt(csvFile.getAbsolutePath(), gzFile.getAbsolutePath());


            Integer iterationsValue = iterations.isPresent()? iterations.get() : 1000;
            Double alphaValue   = alpha.isPresent()? alpha.get() : 0.1;
            Double betaValue    = beta.isPresent()? beta.get() : 0.01;
            Integer topicsValue = numTopics.isPresent()? numTopics.get() : Double.valueOf(2*Math.sqrt(counter.get()/2)).intValue();
            Integer wordsValue  = wordsPerTopic.isPresent()? wordsPerTopic.get() : 10;

            LOG.info("Ready to train a " + algorithm + " model with " + topicsValue + " topics (alpha="+alphaValue+"/beta="+betaValue+") in " + iterationsValue + " iterations" );
            List<String> args = Arrays.asList(new String[]{
                    "-est",
                    "-alpha",String.valueOf(alphaValue),
                    "-beta",String.valueOf(betaValue),
                    "-ntopics",String.valueOf(topicsValue),
                    "-niters",String.valueOf(iterationsValue),
                    "-twords",String.valueOf(wordsValue),
                    "-dir",folder.getAbsolutePath(),
                    "-dfile",gzFile.getName(),
                    "-model","model"
            });



            LDA.main(args.toArray(new String[args.size()]));



        } catch (IOException e) {
            LOG.error("Error",e);
            throw new ModelError("Error creating temporal files",e);
        }





    }


    public void gzipIt(String sourceFile, String gzFile){

        byte[] buffer = new byte[1024];

        try{

            GZIPOutputStream gzos =
                    new GZIPOutputStream(new FileOutputStream(gzFile));

            FileInputStream in =
                    new FileInputStream(sourceFile);

            int len;
            while ((len = in.read(buffer)) > 0) {
                gzos.write(buffer, 0, len);
            }

            in.close();

            gzos.finish();
            gzos.close();

            System.out.println("Done");

        }catch(IOException ex){
            ex.printStackTrace();
        }
    }

}
