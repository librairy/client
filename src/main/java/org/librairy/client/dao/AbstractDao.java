package org.librairy.client.dao;

import org.librairy.client.services.RestService;

/**
 * @author Badenes Olmedo, Carlos <cbadenes@fi.upm.es>
 */
public abstract class AbstractDao {

    protected final RestService api;

    public AbstractDao(RestService api){
        this.api = api;
    }
}
