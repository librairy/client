package org.librairy.client.services;

import com.google.common.collect.ImmutableMap;
import es.cbadenes.lab.test.IntegrationTest;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.librairy.client.LibrairyClient;
import org.librairy.client.exceptions.InvalidCredentialsError;
import org.librairy.client.exceptions.ModelError;
import org.librairy.client.exceptions.StorageError;
import org.librairy.client.model.DataItem;
import org.librairy.client.model.DataModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
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
        this.service    = this.client.connect("librairy.linkeddata.es","oeg","0de4422b52ad");
    }

    @After
    public void close(){
        this.client.disconnect();
    }



    @Test
    public void loadPDFTest() throws IOException {

        Map<String, String> parameters = ImmutableMap.of(
                "lda.optimizer","manual",
                "lda.alpha","0.1",
                "lda.beta","0.01",
                "lda.topics","6",
                "lda.max.iterations","100"
                );

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
                    Optional.empty());
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


}
