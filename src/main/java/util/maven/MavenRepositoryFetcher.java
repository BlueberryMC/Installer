package util.maven;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import util.base.Bytes;
import util.base.Strings;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public class MavenRepositoryFetcher {
    private static final long WEEK = 1000 * 60 * 60 * 24 * 7;
    private final MavenRepository maven;
    private final Repository saveTo;
    private MessageReporter messageReporter = null;

    public MavenRepositoryFetcher(MavenRepository maven, File saveTo) {
        this.maven = maven;
        this.saveTo = Repository.file("libraries", saveTo);
    }

    public MavenRepository getMaven() {
        return maven;
    }

    public MavenRepositoryFetcher withMessageReporter(MessageReporter messageReporter) {
        this.messageReporter = messageReporter;
        return this;
    }

    private void reportMessage(String message, Throwable throwable) {
        Objects.requireNonNull(message, "message cannot be null");
        if (messageReporter != null) messageReporter.onError(message, throwable);
    }

    private void check() {
        if (maven.getRepositories().isEmpty()) throw new IllegalArgumentException("Repositories list is empty");
    }

    // true if last updated < a week; false otherwise
    private boolean checkLastUpdated(String path) {
        File file = new File(path + ".last_updated");
        try {
            long lastUpdated = Long.parseLong(Strings.readStringThenClose(file));
            if (System.currentTimeMillis() - lastUpdated < WEEK) return true;
        } catch (IOException | NumberFormatException ignore) {}
        try {
            Strings.writeStringThenClose(file, Long.toString(System.currentTimeMillis()));
        } catch (IOException ignore) {}
        return false;
    }

    /**
     * Fetch the pom file of dependency and returns the collected dependencies.
     * @param dependency the dependency
     * @return dependencies of the <code>dependency</code>, without the <code>dependency</code> itself
     */
    public List<Dependency> collectDependencies(Dependency dependency) {
        check();
        if (dependency.getClassifier().length() != 0) return Collections.emptyList();
        Dependency pom = dependency.pom();
        String localPath = saveTo.resolve(pom);
        File xmlFile = new File(localPath);
        //noinspection ResultOfMethodCallIgnored
        xmlFile.getParentFile().mkdirs();
        boolean isDirty = !checkLastUpdated(localPath);
        if (dependency.getSha512() != null) {
            try {
                isDirty = !compareSha512(fetchBytes(localPath, true), fromHex(dependency.getSha512()));
            } catch (IOException ignore) {}
        }
        if (isDirty) {
            IOException exception = null;
            for (Repository repository : maven.getRepositories()) {
                try {
                    String url = repository.resolve(pom);
                    reportMessage("Downloading " + url + " from " + repository.getName(), null);
                    if (pom.getSha512() != null && !compareSha512(fetchBytes(url, repository.isFile()), fromHex(pom.getSha512()))) {
                        reportMessage("Failed to verify " + url + " (expected: " + pom.getSha512() + ")", null);
                        continue;
                    }
                    String xml = fetchText(url, repository.isFile());
                    try (FileOutputStream fos = new FileOutputStream(xmlFile);
                         OutputStreamWriter osw = new OutputStreamWriter(fos);
                         BufferedWriter bw = new BufferedWriter(osw)) {
                        bw.write(xml);
                        bw.flush();
                    }
                    reportMessage("Downloaded " + url + " from " + repository.getName(), null);
                    exception = null;
                    break;
                } catch (IOException e) {
                    exception = e;
                }
            }
            if (exception != null) {
                reportMessage("Failed to download dependency " + dependency, exception);
                return Collections.emptyList();
            }
        }
        if (!xmlFile.exists()) {
            reportMessage("POM does not exist at " + xmlFile.getAbsolutePath() + ", skipping", null);
            return Collections.emptyList();
        }
        Document document;
        NodeList dependencies;
        try {
            document = DocumentBuilderFactory
                    .newInstance()
                    .newDocumentBuilder()
                    .parse(xmlFile);
            dependencies = Optional.ofNullable(document.getElementsByTagName("dependencies").item(0)).map(Node::getChildNodes).orElse(null);
        } catch (SAXException | IOException | ParserConfigurationException e) {
            reportMessage("Failed to parse pom from " + xmlFile.getAbsolutePath(), e);
            return Collections.emptyList();
        }
        if (dependencies == null || dependencies.getLength() == 0) return Collections.emptyList();
        Map<String, String> properties = new HashMap<>();
        NodeList propertiesElement = Optional.ofNullable(document.getElementsByTagName("properties").item(0)).map(Node::getChildNodes).orElse(null);
        if (propertiesElement != null && propertiesElement.getLength() > 0) {
            for (int i = 0; i < propertiesElement.getLength(); i++) {
                Node node = propertiesElement.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    //noinspection ResultOfMethodCallIgnored
                    node.getNodeValue();
                    properties.put(node.getNodeName(), node.getTextContent());
                }
            }
        }
        List<Dependency> dependencyList = new ArrayList<>();
        Function<String, String> toValueFunction = s -> {
            if (s.matches("^\\$\\{.*}$")) {
                String key = s.replaceAll("^\\$\\{(.*)}$", "$1");
                String str = properties.get(key);
                if (str == null) return key;
                return str;
            }
            if (s.matches("^\\[.*,$")) return s.replaceAll("^\\[(.*),$", "$1");
            if (s.matches("^\\[(.*)]$")) {
                String[] arr = s.replaceAll("^\\[(.*)]$", "$1").split(",");
                return arr[arr.length - 1];
            }
            return s;
        };
        dep: for (int i = 0; i < dependencies.getLength(); i++) {
            String groupId = null;
            String artifactId = null;
            String version = null;
            String classifier = "";
            String type = "jar";
            NodeList nodes = dependencies.item(i).getChildNodes();
            for (int j = 0; j < nodes.getLength(); j++) {
                Node node = nodes.item(j);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    //noinspection ResultOfMethodCallIgnored
                    node.getNodeValue();
                    switch (node.getNodeName()) {
                        case "groupId" -> groupId = toValueFunction.apply(node.getTextContent());
                        case "artifactId" -> artifactId = toValueFunction.apply(node.getTextContent());
                        case "version" -> version = toValueFunction.apply(node.getTextContent());
                        case "classifier" -> classifier = toValueFunction.apply(node.getTextContent());
                        case "type" -> type = toValueFunction.apply(node.getTextContent());
                        case "scope" -> {
                            String scope = toValueFunction.apply(node.getTextContent());
                            if (scope != null && scope.length() != 0 && !scope.equals("compile") && !scope.equals("provided")/* && !scope.equals("runtime")*/)
                                continue dep;
                        }
                    }
                }
            }
            if (groupId == null || artifactId == null || version == null) continue;
            dependencyList.add(Dependency.resolve(groupId, artifactId, version, classifier, type));
        }
        return dependencyList;
    }

    /**
     * Download the dependency.
     * @param dependency the dependency
     * @return downloaded file, null if failed
     */
    public File downloadFile(Dependency dependency) {
        check();
        String localPath = saveTo.resolve(dependency);
        File file = new File(localPath);
        //noinspection ResultOfMethodCallIgnored
        file.getParentFile().mkdirs();
        if (checkLastUpdated(localPath)) return file.exists() ? file : null;
        if (dependency.getSha512() != null) {
            try {
                if (compareSha512(fetchBytes(localPath, true), fromHex(dependency.getSha512()))) {
                    return file;
                }
            } catch (IOException ignore) {}
        }
        IOException exception = null;
        for (Repository repository : maven.getRepositories()) {
            try {
                String url = repository.resolve(dependency);
                reportMessage("Downloading " + url + " from " + repository.getName(), null);
                byte[] bytes = fetchBytes(url, repository.isFile());
                Bytes.copy(new ByteArrayInputStream(bytes), file);
                reportMessage("Downloaded " + url + " from " + repository.getName(), null);
                exception = null;
                break;
            } catch (IOException e) {
                exception = e;
            }
        }
        if (exception != null) {
            reportMessage("Failed to download dependency " + dependency, exception);
            return null;
        }
        return file;
    }

    public Set<Dependency> collectAllDependencies(Dependency dependency) {
        Set<Dependency> dependencies = new HashSet<>(collectDependencies(dependency));
        for (Dependency dep : new HashSet<>(dependencies)) {
            dependencies.addAll(collectAllDependencies(dep));
        }
        return dependencies;
    }

    /**
     * Download all dependencies defined in {@link MavenRepository#getDependencies()}.
     * @return list of files, may contain null if some dependency fails to download.
     */
    public List<File> downloadAllDependencies() {
        List<File> files = new ArrayList<>();
        Set<Dependency> toDownload = new HashSet<>();
        for (Dependency dependency : maven.getDependencies()) {
            toDownload.addAll(collectAllDependencies(dependency));
        }
        for (Map.Entry<String, String> entry : maven.getExclude()) {
            toDownload.removeIf(dep -> dep.getGroupId().equals(entry.getKey()) && dep.getArtifactId().equals(entry.getValue()));
        }
        for (Dependency dependency : maven.getDependencies()) {
            toDownload.removeIf(dep -> dep.getGroupId().equals(dependency.getGroupId()) && dep.getArtifactId().equals(dependency.getArtifactId()));
        }
        toDownload.addAll(maven.getDependencies());
        for (Dependency dependency : toDownload) {
            files.add(downloadFile(dependency));
        }
        return files;
    }

    private static byte[] fromHex(String s) {
        if (s.length() % 2 != 0) throw new IllegalArgumentException("Hex " + s + " must be divisible by two");
        byte[] bytes = new byte[s.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            char left = s.charAt(i * 2);
            char right = s.charAt(i * 2 + 1);
            byte b = (byte) ((getValue(left) << 4) | (getValue(right) & 0xF));
            bytes[i] = b;
        }
        return bytes;
    }

    private static int getValue(char c) {
        int i = Character.digit(c, 16);
        if (i < 0) throw new IllegalArgumentException("Invalid hex char: " + c);
        return i;
    }

    private static boolean compareSha512(byte[] bytes, byte[] expected) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-512");
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("Could not find SHA-512 hashing instance");
        }
        return Arrays.equals(digest.digest(bytes), expected);
    }

    public static String fetchText(String url, boolean isFile) throws IOException {
        InputStream in;
        if (isFile) {
            in = new FileInputStream(url);
        } else {
            URLConnection connection = new URL(url).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.setDoInput(true);
            connection.connect();
            if (connection instanceof HttpURLConnection httpURLConnection) {
                int code = httpURLConnection.getResponseCode();
                if (code < 200 || code > 299) throw new IOException("Non 2xx response code: " + code);
            }
            in = connection.getInputStream();
        }
        BufferedReader br = new BufferedReader(new InputStreamReader(in));
        StringBuilder sb = new StringBuilder();
        String output;
        while ((output = br.readLine()) != null) sb.append(output);
        in.close();
        return sb.toString();
    }

    public static byte[] fetchBytes(String url, boolean isFile) throws IOException {
        if (isFile) return Bytes.readFully(new FileInputStream(url));
        URLConnection connection = new URL(url).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        connection.setDoInput(true);
        connection.connect();
        if (connection instanceof HttpURLConnection httpURLConnection) {
            int code = httpURLConnection.getResponseCode();
            if (code < 200 || code > 299) throw new IOException("Non 2xx response code: " + code);
        }
        try (InputStream in = connection.getInputStream()) {
            return Bytes.readFully(in);
        }
    }
}
