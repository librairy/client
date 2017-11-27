package org.librairy.client.exceptions;

/**
 * @author Badenes Olmedo, Carlos <cbadenes@fi.upm.es>
 */
public class InvalidCredentialsError extends Exception {

    public InvalidCredentialsError(String msg, Throwable e){
        super(msg,e);
    }

    public InvalidCredentialsError(String msg){
        super(msg);
    }
}
