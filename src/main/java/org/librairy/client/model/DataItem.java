package org.librairy.client.model;

import com.google.common.base.Strings;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * @author Badenes Olmedo, Carlos <cbadenes@fi.upm.es>
 */
@Data
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public class DataItem {

    private String name;

    private String content;

    private LANGUAGE language;

    public boolean isEmpty(){
        return Strings.isNullOrEmpty(name) || Strings.isNullOrEmpty(content);
    }

    public enum LANGUAGE{
        EN,ES;
    }
}
