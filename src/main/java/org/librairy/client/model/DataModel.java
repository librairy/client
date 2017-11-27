package org.librairy.client.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Badenes Olmedo, Carlos <cbadenes@fi.upm.es>
 */
@Data
public class DataModel {

    List<DataTopic> topics = new ArrayList<>();

    public void add(DataTopic topic){
        this.topics.add(topic);
    }

    public enum ALGORITHM{
        LDA,LLDA;
    }
}
