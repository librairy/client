package org.librairy.client.services;

import com.google.common.escape.Escaper;
import com.google.common.escape.Escapers;

/**
 * @author Badenes Olmedo, Carlos <cbadenes@fi.upm.es>
 */
public class TextService {

    private static final Escaper escaper = Escapers.builder()
            .addEscape('\'',"")
            .addEscape('\"',"")
            .addEscape('('," ")
            .addEscape(')'," ")
            .addEscape('['," ")
            .addEscape(']'," ")
            .addEscape('\n'," ")
            .build();

    public static String escape(String text){
        return escaper.escape(text);
    }
}
