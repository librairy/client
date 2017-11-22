package org.librairy.client.services;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.IOException;

/**
 * @author Badenes Olmedo, Carlos <cbadenes@fi.upm.es>
 */
public class PdfService {

    public static String toString(File pdf) throws IOException {
        PDDocument pdDocument = PDDocument.load(pdf);
        String text = new PDFTextStripper().getText(pdDocument);
        pdDocument.close();
        return text;
    }
}
