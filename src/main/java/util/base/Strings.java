package util.base;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

public class Strings {
    public static void writeStringThenClose(File dest, String s) throws IOException {
        writeStringThenClose(new FileOutputStream(dest), s);
    }

    public static void writeStringThenClose(OutputStream out, String s) throws IOException {
        try (OutputStreamWriter outputStreamWriter = new OutputStreamWriter(out);
             BufferedWriter bufferedWriter = new BufferedWriter(outputStreamWriter)) {
            bufferedWriter.write(s);
        }
    }

    public static String readStringThenClose(File src) throws IOException {
        return readStringThenClose(new FileInputStream(src));
    }

    public static String readStringThenClose(InputStream in) throws IOException {
        try (InputStreamReader inputStreamReader = new InputStreamReader(in);
             BufferedReader reader = new BufferedReader(inputStreamReader)) {
            StringBuilder builder = new StringBuilder();
            String s;
            while ((s = reader.readLine()) != null) builder.append(s);
            return builder.toString();
        }
    }

    public static List<String> readLines(File src) throws IOException {
        return readLines(new FileInputStream(src));
    }

    public static List<String> readLines(InputStream in) throws IOException {
        try (InputStreamReader inputStreamReader = new InputStreamReader(in);
             BufferedReader reader = new BufferedReader(inputStreamReader)) {
            List<String> list = new ArrayList<>();
            String s;
            while ((s = reader.readLine()) != null) list.add(s);
            return list;
        }
    }
}
