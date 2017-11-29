package org.librairy.client.model;

import lombok.Data;

/**
 * @author Badenes Olmedo, Carlos <cbadenes@fi.upm.es>
 */
@Data
public class TopicDistance {

    public enum ALGORITHM{
        RANK, EXTKENDALLSTAU;
    }
}
