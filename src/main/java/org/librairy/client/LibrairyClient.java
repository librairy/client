package org.librairy.client;

import org.librairy.boot.Config;
import org.librairy.client.services.LibrairyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * @author Badenes Olmedo, Carlos <cbadenes@fi.upm.es>
 */
public class LibrairyClient {


    private static final Logger LOG = LoggerFactory.getLogger(LibrairyClient.class);


    private AnnotationConfigApplicationContext context;


    public LibrairyService connect(String host){
        System.setProperty("librairy.uri",(host.equalsIgnoreCase("localhost"))? host+":8080/api" : host+"/api");
        System.setProperty("librairy.eventbus.host",host);
        System.setProperty("librairy.columndb.host",host);

        this.context = new AnnotationConfigApplicationContext(Config.class);

        LibrairyService service = new LibrairyService(context);

        LOG.info("  ٩(͡๏̯͡๏)۶  connected to librAIry in: " + host);

        return service;
    }

    public void disconnect(){
        if (this.context != null) this.context.destroy();
        LOG.info("  disconnected from librAIry");
    }

}
