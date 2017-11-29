package org.librairy.client.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * @author Badenes Olmedo, Carlos <cbadenes@fi.upm.es>
 */
@Data
@EqualsAndHashCode
@ToString
@AllArgsConstructor
public class DataSimilarity {

    String source;
    String target;
    Double score;
}
