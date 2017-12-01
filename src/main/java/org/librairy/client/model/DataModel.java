package org.librairy.client.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Badenes Olmedo, Carlos <cbadenes@fi.upm.es>
 */
@Data
public class DataModel {

    List<Topic> topics = new ArrayList<>();

    List<Shape> shapes = new ArrayList<>();

    public void add(Topic topic){
        this.topics.add(topic);
    }

    public void add(Shape shape){
        this.shapes.add(shape);
    }

    public enum ALGORITHM{
        LDA
    }
}
