package org.librairy.client;

import org.junit.Assert;
import org.junit.Test;
import org.librairy.client.services.LibrairyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Badenes Olmedo, Carlos <cbadenes@fi.upm.es>
 */
//@Category(IntegrationTest.class)
public class LibrairyClientTest {


    private static final Logger LOG = LoggerFactory.getLogger(LibrairyClient.class);

    @Test
    public void boot(){

        LibrairyClient client = new LibrairyClient();

        LibrairyService service = client.connect("localhost");

        client.disconnect();

    }
}
