package org.librairy.client.services;

import es.cbadenes.lab.test.IntegrationTest;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.librairy.client.LibrairyClient;
import org.librairy.client.exceptions.ModelError;
import org.librairy.client.exceptions.StorageError;
import org.librairy.client.model.DataItem;
import org.librairy.client.model.DataModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * @author Badenes Olmedo, Carlos <cbadenes@fi.upm.es>
 */
@Category(IntegrationTest.class)
public class LibrairyServiceTest {

    private static final Logger LOG = LoggerFactory.getLogger(LibrairyServiceTest.class);

    private LibrairyClient client;
    private LibrairyService service;

    @Before
    public void setup(){
        this.client     = new LibrairyClient();
        this.service    = this.client.connect("localhost");
    }

    @After
    public void close(){
        this.client.disconnect();
    }

    @Test
    public void crudItemTest(){

        DataItem item = new DataItem("Distributing text mining tasks with librAIry","text and text and text", DataItem.LANGUAGE.EN);

        List<String> domains = Arrays.asList("domain1", "domain2", "domain3");

        try {
            this.service.newItem(item, domains);
        } catch (StorageError storageError) {
            Assert.fail(storageError.getMessage());
        }

        for(String domain : domains){
            try {
                this.service.deleteDomain(domain);
            } catch (StorageError storageError) {
                Assert.fail(storageError.getMessage());
            }
        }

        try {
            this.service.deleteItem(item.getName());
        } catch (StorageError storageError) {
            Assert.fail(storageError.getMessage());
        }

    }

    @Test
    public void deleteAllTest(){

        service.deleteAll();

    }

    @Test
    public void loadPDFTest() throws IOException {


        service.addFolder("/Users/cbadenes/Documents/Academic/DoctoradoIA/congresos/2017/KCap/tutorial/papers/K-CAP 2015 camera ready papers - corpus",Arrays.asList(new String[]{"kcap","kcap-2015"}));
        service.addFolder("/Users/cbadenes/Documents/Academic/DoctoradoIA/congresos/2017/KCap/tutorial/papers/K-CAP 2017 camera ready papers - corpus",Arrays.asList(new String[]{"kcap","kcap-2017"}));

    }

    @Test
    public void topicModelTest(){
        try {
            service.newModel(
                    DataModel.ALGORITHM.LDA,
                    "kcap",
                    Optional.of(1000),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty());
        } catch (ModelError modelError) {
            Assert.fail(modelError.getMessage());
        }
    }


}
