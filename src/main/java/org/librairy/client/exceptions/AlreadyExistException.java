package org.librairy.client.exceptions;

import java.io.IOException;

/**
 * @author Badenes Olmedo, Carlos <cbadenes@fi.upm.es>
 */
public class AlreadyExistException extends IOException {

    public AlreadyExistException(String msg, Throwable e){
        super(msg,e);
    }

    public AlreadyExistException(String msg){
        super(msg);
    }
}
