package org.librairy.client;

import org.librairy.client.exceptions.InvalidCredentialsError;
import org.librairy.client.services.LibrairyService;
import org.librairy.client.services.RestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Badenes Olmedo, Carlos <cbadenes@fi.upm.es>
 */
public class LibrairyClient {


    private static final Logger LOG = LoggerFactory.getLogger(LibrairyClient.class);


    public LibrairyService connect(String host, String user, String pwd) throws InvalidCredentialsError {


        RestService restService = new RestService("http://"+host+"/api", user, pwd);

        // check
        restService.get("/domains");

        LOG.info("Connecting to librAIry ["+host+"] ...");
        System.setProperty("librairy.uri",(host.equalsIgnoreCase("localhost"))? host+":8080/api" : host+"/resources");
        System.setProperty("librairy.eventbus.host",host);
        System.setProperty("librairy.columndb.host",host);

        LibrairyService service = new LibrairyService(restService);

        LOG.info("  ٩(͡๏̯͡๏)۶  connected to librAIry in: " + host);

        return service;
    }

    public void disconnect(){
        LOG.info("  disconnected from librAIry");
    }


}
