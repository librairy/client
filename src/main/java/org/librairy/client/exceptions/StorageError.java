package org.librairy.client.exceptions;

/**
 * @author Badenes Olmedo, Carlos <cbadenes@fi.upm.es>
 */
public class StorageError extends Exception {

    public StorageError(String msg, Throwable e){
        super(msg,e);
    }

    public StorageError(String msg){
        super(msg);
    }
}
