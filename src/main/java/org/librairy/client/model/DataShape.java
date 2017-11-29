package org.librairy.client.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.List;

/**
 * @author Badenes Olmedo, Carlos <cbadenes@fi.upm.es>
 */
@Data
@EqualsAndHashCode
@ToString
public class DataShape {

    String uri;

    List<Double> vector;

    double[] vectorArray;

    public void setVector(List<Double> vector){
        this.vector     = vector;
        vectorArray     = vector.stream().mapToDouble(Double::doubleValue).toArray();
    }

}
