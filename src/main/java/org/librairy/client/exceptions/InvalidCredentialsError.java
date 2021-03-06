package org.librairy.client.exceptions;

import java.io.IOException;

/**
 * @author Badenes Olmedo, Carlos <cbadenes@fi.upm.es>
 */
public class InvalidCredentialsError extends IOException {

    public InvalidCredentialsError(String msg, Throwable e){
        super(msg,e);
    }

    public InvalidCredentialsError(String msg){
        super(msg);
    }
}
