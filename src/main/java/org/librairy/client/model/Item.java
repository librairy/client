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
public class Item {

    String id;

    String name;

    String content;

    String language;

    String uri;

    String creationTime;

}
