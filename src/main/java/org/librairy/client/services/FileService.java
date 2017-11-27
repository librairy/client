package org.librairy.client.services;

import java.io.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * @author Badenes Olmedo, Carlos <cbadenes@fi.upm.es>
 */
public class FileService {

    public static void gzipIt(String sourceFile, String gzFile){

        byte[] buffer = new byte[1024];

        try{

            GZIPOutputStream gzos =
                    new GZIPOutputStream(new FileOutputStream(gzFile));

            FileInputStream in =
                    new FileInputStream(sourceFile);

            int len;
            while ((len = in.read(buffer)) > 0) {
                gzos.write(buffer, 0, len);
            }

            in.close();

            gzos.finish();
            gzos.close();

            System.out.println("Done");

        }catch(IOException ex){
            ex.printStackTrace();
        }
    }

    public static BufferedReader reader(String file) throws IOException {
        BufferedReader reader;
        if (file.endsWith("gz")){
            reader = new BufferedReader(new InputStreamReader(
                    new GZIPInputStream(
                            new FileInputStream(file)), "UTF-8"));
        }else{
            reader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(file), "UTF-8"));
        }
        return reader;
    }

    public static BufferedWriter writer(String file) throws IOException {
        BufferedWriter writer;

        if (file.endsWith("gz")){
            writer = new BufferedWriter(new OutputStreamWriter(
                    new GZIPOutputStream(
                            new FileOutputStream(file)), "UTF-8"));
        }else{
            writer = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(file), "UTF-8"));
        }
        return writer;
    }

}
