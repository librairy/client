package org.librairy.client;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.librairy.boot.Config;
import org.librairy.client.exceptions.InvalidCredentialsError;
import org.librairy.client.services.LibrairyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOError;
import java.net.URI;

/**
 * @author Badenes Olmedo, Carlos <cbadenes@fi.upm.es>
 */
public class LibrairyClient {


    private static final Logger LOG = LoggerFactory.getLogger(LibrairyClient.class);


    private AnnotationConfigApplicationContext context;


    public LibrairyService connect(String host, String user, String pwd) throws InvalidCredentialsError {


        try {
            HttpResponse<JsonNode> response = Unirest.get("http://"+host+"/api/domains").basicAuth(user, pwd).asJson();
            int status = response.getStatus();
            if (status == 401){
                throw new InvalidCredentialsError("User and/or password invalid in librAIry: " + host);
            }
        } catch (UnirestException e) {
            new IOError(e);
        }


        LOG.info("Connecting to librAIry ["+host+"] ...");
        System.setProperty("librairy.uri",(host.equalsIgnoreCase("localhost"))? host+":8080/api" : host+"/resources");
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
