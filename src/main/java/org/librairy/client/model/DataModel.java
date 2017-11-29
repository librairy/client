package org.librairy.client.model;

import lombok.Data;
import org.librairy.boot.model.domain.resources.Shape;
import org.librairy.boot.model.domain.resources.TopicDescription;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Badenes Olmedo, Carlos <cbadenes@fi.upm.es>
 */
@Data
public class DataModel {

    List<TopicDescription> topics = new ArrayList<>();

    List<Shape> shapes = new ArrayList<>();

    public void add(TopicDescription topic){
        this.topics.add(topic);
    }

    public void add(Shape shape){
        this.shapes.add(shape);
    }

    public enum ALGORITHM{
        LDA;
    }
}
