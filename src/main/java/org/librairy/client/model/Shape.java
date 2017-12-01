package org.librairy.client.model;

import lombok.Data;

import java.util.List;

/**
 * @author Badenes Olmedo, Carlos <cbadenes@fi.upm.es>
 */
@Data
public class Shape {

    String uri;

    String type;

    List<Double> vector;

}
