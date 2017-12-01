package org.librairy.client.services;

import es.cbadenes.lab.test.IntegrationTest;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.librairy.client.LibrairyClient;
import org.librairy.client.exceptions.InvalidCredentialsError;
import org.librairy.client.exceptions.ModelError;
import org.librairy.client.model.DataModel;
import org.librairy.metrics.distance.ExtendedKendallsTauSimilarity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * @author Badenes Olmedo, Carlos <cbadenes@fi.upm.es>
 */
@Category(IntegrationTest.class)
public class LibrairyServiceTest {

    private static final Logger LOG = LoggerFactory.getLogger(LibrairyServiceTest.class);

    private LibrairyClient client;
    private LibrairyService service;

    @Before
    public void setup() throws InvalidCredentialsError {
        this.client     = new LibrairyClient();
        this.service    = this.client.connect("librairy.linkeddata.es","oeg","kcap2017");
    }

    @After
    public void close(){
        this.client.disconnect();
    }



    @Test
    public void loadPDFTest() throws IOException {

        Map<String, String> parameters = new HashMap<>();
        parameters.put("lda.optimizer","manual");
        parameters.put("lda.alpha","0.1");
        parameters.put("lda.beta","0.01");
        parameters.put("lda.topics","6");
        parameters.put("lda.max.iterations","100");
        parameters.put("lda.stopwords","figure,section,example");


        service.addFolder("/Users/cbadenes/Documents/Academic/DoctoradoIA/congresos/2017/KCap/tutorial/papers/K-CAP 2015 camera ready papers - corpus",Arrays.asList(new String[]{"kcap","kcap2015"}), Optional.of(parameters));
        service.addFolder("/Users/cbadenes/Documents/Academic/DoctoradoIA/congresos/2017/KCap/tutorial/papers/K-CAP 2017 camera ready papers - corpus",Arrays.asList(new String[]{"kcap","kcap2017"}), Optional.of(parameters));

    }

    @Test
    public void topicModelTest(){
        try {
            service.newModel(
                    DataModel.ALGORITHM.LDA,
                    "kcap",
                    Optional.of(Arrays.asList(new String[]{"datum","ontology"})),
                    Optional.of(1000),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.of(10));
        } catch (ModelError modelError) {
            Assert.fail(modelError.getMessage());
        }
    }

    @Test
    public void inferenceTopicModelTest(){
        try {
            File input = new File("input.txt");
            service.inference(
                    "kcap",
                    input,
                    Optional.empty(),
                    Optional.empty());
        } catch (ModelError modelError) {
            Assert.fail(modelError.getMessage());
        }
    }


    @Test
    public void comparisonModelsTest(){
        try {
            service.compare("kcap2015","kcap2017", new ExtendedKendallsTauSimilarity(), Optional.of(25));
        } catch (ModelError modelError) {
            Assert.fail(modelError.getMessage());
        }
    }

    @Test
    public void similaritiesTest(){
        try {
            service.similarities("ecommerce", Optional.of(0.5));
        } catch (ModelError modelError) {
            Assert.fail(modelError.getMessage());
        }
    }


}
