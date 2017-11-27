package org.librairy.client.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Badenes Olmedo, Carlos <cbadenes@fi.upm.es>
 */
@Data
public class DataTopic {

    String id;

    List<DataWord> words = new ArrayList<>();

    public void add(DataWord word){
        this.words.add(word);
    }
}
