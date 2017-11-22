package org.librairy.client.exceptions;

/**
 * @author Badenes Olmedo, Carlos <cbadenes@fi.upm.es>
 */
public class ModelError extends Exception {

    public ModelError(String msg, Throwable e){
        super(msg,e);
    }

    public ModelError(String msg){
        super(msg);
    }
}
