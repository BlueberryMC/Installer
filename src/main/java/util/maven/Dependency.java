package util.maven;

import java.util.Objects;

public class Dependency {
    private final String groupId;
    private final String artifactId;
    private final String version;
    private final String classifier;
    private final String type;
    private String sha512 = null;
    private String pomSha512 = null;

    private Dependency(String groupId, String artifactId, String version, String classifier, String type) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.classifier = classifier;
        this.type = type;
    }

    public Dependency withSha512(String sha512) {
        this.sha512 = sha512;
        return this;
    }

    public Dependency withPomSha512(String sha512) {
        this.pomSha512 = sha512;
        return this;
    }

    public String getSha512() {
        return sha512;
    }

    public String getPomSha512() {
        return pomSha512;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getVersion() {
        return version;
    }

    public String getClassifier() {
        return classifier;
    }

    public String getType() {
        return type;
    }

    public Dependency jar() {
        return new Dependency(groupId, artifactId, version, classifier, "jar");
    }

    public Dependency pom() {
        return new Dependency(groupId, artifactId, version, classifier, "pom").withSha512(pomSha512).withPomSha512(pomSha512);
    }

    public Dependency copyWithoutHash() {
        return new Dependency(groupId, artifactId, version, classifier, type);
    }

    @Override
    public String toString() {
        return "Dependency{" +
                "groupId='" + groupId + '\'' +
                ", artifactId='" + artifactId + '\'' +
                ", version='" + version + '\'' +
                ", classifier='" + classifier + '\'' +
                ", type='" + type + '\'' +
                ", sha512='" + sha512 + '\'' +
                '}';
    }

    public String toNotation() {
        String notation = "";
        if (groupId != null) {
            notation += groupId + ":";
        }
        if (artifactId != null) {
            notation += artifactId + ":";
        }
        if (version != null) {
            notation += version + ":";
        }
        if (type != null) {
            notation += type + ":";
        }
        if (classifier != null) {
            if (classifier.startsWith("-")) {
                notation += classifier.substring(1) + ":";
            } else {
                notation += classifier + ":";
            }
        }
        return notation.substring(0, notation.length() - 1);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Dependency that = (Dependency) o;
        return groupId.equals(that.groupId)
                && artifactId.equals(that.artifactId)
                && version.equals(that.version)
                && classifier.equals(that.classifier)
                && type.equals(that.type)
                && Objects.equals(sha512, that.sha512);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, artifactId, version, classifier, type, sha512);
    }

    public static Dependency resolve(String dependencyNotation) {
        String[] split = dependencyNotation.split(":");
        if (split.length < 3) throw new IllegalArgumentException("groupId, artifactId, version are required (groupId:artifactId:version[[:type][:classifier]])");
        String groupId = split[0];
        String artifactId = split[1];
        String version = split[2];
        String type = "jar";
        if (split.length >= 4) type = split[3];
        String classifier = "";
        if (split.length >= 5) classifier = "-" + split[4];
        return resolve(groupId, artifactId, version, classifier, type);
    }

    public static Dependency resolve(String groupId, String artifactId, String version) {
        return new Dependency(groupId, artifactId, version, "", "jar");
    }

    public static Dependency resolve(String groupId, String artifactId, String version, String type) {
        return new Dependency(groupId, artifactId, version, "", type);
    }

    public static Dependency resolve(String groupId, String artifactId, String version, String classifier, String type) {
        return new Dependency(groupId, artifactId, version, classifier, type);
    }
}
