package org.librairy.client.model;

import lombok.Data;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * @author Badenes Olmedo, Carlos <cbadenes@fi.upm.es>
 */
@Data
public class Space {

    ConcurrentLinkedQueue<Item> resources = new ConcurrentLinkedQueue<>();

    ConcurrentLinkedQueue<DataSimilarity> similarities = new ConcurrentLinkedQueue<>();


    public void add(Item item){
        this.resources.add(item);
    }

    public void add(DataSimilarity similarity){
        this.similarities.add(similarity);
    }
}
