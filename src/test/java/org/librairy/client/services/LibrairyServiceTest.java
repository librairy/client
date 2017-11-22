package org.librairy.client.services;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.librairy.client.LibrairyClient;
import org.librairy.client.exceptions.ModelError;
import org.librairy.client.exceptions.StorageError;
import org.librairy.client.model.DataItem;
import org.librairy.client.model.DataModel;

import java.util.Arrays;
import java.util.List;

/**
 * @author Badenes Olmedo, Carlos <cbadenes@fi.upm.es>
 */
//@Category(IntegrationTest.class)
public class LibrairyServiceTest {


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
    public void lldaTest(){
        try {
            service.newModel(DataModel.ALGORITHM.LLDA,"domain1",1000);
        } catch (ModelError modelError) {
            Assert.fail(modelError.getMessage());
        }
    }

}
