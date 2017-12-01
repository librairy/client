package org.librairy.client.services;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.librairy.client.exceptions.AlreadyExistException;
import org.librairy.client.exceptions.InvalidCredentialsError;

import java.io.IOError;
import java.util.Map;

/**
 * @author Badenes Olmedo, Carlos <cbadenes@fi.upm.es>
 */
public class RestService {


    private final String baseUrl;
    private final String user;
    private final String pwd;

    public RestService(String baseUrl, String user, String pwd){
        this.baseUrl    = baseUrl;
        this.user       = user;
        this.pwd        = pwd;
    }

    public JsonNode get(String path) throws InvalidCredentialsError {
        try {
            return request(Unirest.get(baseUrl + path).basicAuth(user, pwd).asJson());
        } catch (UnirestException e) {
            throw  new IOError(e);
        } catch (AlreadyExistException e) {
            throw new RuntimeException("AlreadyExistException");
        }
    }

    public JsonNode put(String path) throws InvalidCredentialsError {
        try {
            return request(Unirest.put(baseUrl + path).basicAuth(user, pwd).asJson());
        } catch (UnirestException e) {
            throw  new IOError(e);
        } catch (AlreadyExistException e) {
            throw new RuntimeException("AlreadyExistException");
        }
    }


    public JsonNode post(String path, Map<String,Object> fields) throws InvalidCredentialsError, AlreadyExistException {
        try {
            return request(Unirest.post(baseUrl + path).basicAuth(user, pwd).fields(fields).asJson());
        } catch (UnirestException e) {
            throw  new IOError(e);
        }
    }

    public String uriOf(String type, String id){
        return baseUrl+"/"+type+"s/"+id;
    }

    private JsonNode request(HttpResponse<JsonNode> response) throws IOError, InvalidCredentialsError, AlreadyExistException {
        int status = response.getStatus();
        if (status == 401) {
            throw new InvalidCredentialsError("User and/or password invalid in librAIry: " + baseUrl);
        } else if (status == 409){
            throw new AlreadyExistException("Resource alredy exists");
        } else if (status != 200 && status != 201){
            throw new IllegalArgumentException("Request error ["+status+"]");
        }
        return response.getBody();
    }
}
