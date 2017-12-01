package org.librairy.client.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Badenes Olmedo, Carlos <cbadenes@fi.upm.es>
 */
@Data
public class Topic {

    String id;

    Double score;

    List<Word> words = new ArrayList<>();

    public void add(Word word){
        words.add(word);
    }
}
