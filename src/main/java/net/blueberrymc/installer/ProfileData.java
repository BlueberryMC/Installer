package net.blueberrymc.installer;

import util.base.Strings;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

public class ProfileData {
    public static String name = "minecraft-" + System.currentTimeMillis();
    public static boolean hideClient = false;
    public static boolean hideServer = false;
    public static boolean hideExtract = false;
    public static List<String> serverRepositories = new ArrayList<>();
    public static List<String> serverLibraries = new ArrayList<>();
    public static List<String> serverFiles = new ArrayList<>();
    public static List<String> extractFiles = new ArrayList<>();

    public static void read() throws IOException {
        try (InputStream in = ProfileData.class.getResourceAsStream("/profile.properties")) {
            if (in == null) throw new FileNotFoundException("/profile.properties");
            Properties properties = new Properties();
            properties.load(new BufferedReader(new InputStreamReader(in)));
            name = Objects.requireNonNull(properties.getProperty("name", "minecraft-" + System.currentTimeMillis()));
            hideClient = Boolean.parseBoolean(properties.getProperty("hideClient"));
            hideServer = Boolean.parseBoolean(properties.getProperty("hideServer"));
            hideExtract = Boolean.parseBoolean(properties.getProperty("hideExtract"));
            readList(serverRepositories, properties.getProperty("serverRepositories"));
            readList(serverLibraries, properties.getProperty("serverLibraries"));
            serverFiles.clear();
            for (String s : properties.getProperty("serverFiles", "").split(",")) {
                if (s.isEmpty()) continue;
                serverFiles.add(s);
            }
            extractFiles.clear();
            for (String s : properties.getProperty("extractFiles", "").split(",")) {
                if (s.isEmpty()) continue;
                extractFiles.add(s);
            }
        }
    }

    private static void readList(List<String> list, String path) throws IOException {
        list.clear();
        InputStream in = ProfileData.class.getResourceAsStream(path);
        if (in != null) {
            list.addAll(Strings.readLines(in));
        }
    }
}
