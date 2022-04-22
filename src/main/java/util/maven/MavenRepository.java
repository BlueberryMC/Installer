package util.maven;

import java.io.File;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MavenRepository {
    private final List<Repository> repositories = new ArrayList<>();
    private final List<Dependency> dependencies = new ArrayList<>();
    private final List<Map.Entry<String, String>> exclude = new ArrayList<>();

    public void addRepository(Repository repository) {
        repositories.add(repository);
    }

    public void addDependency(Dependency dependency) {
        dependencies.add(dependency);
    }

    public void addExclude(String groupId, String artifactId) {
        exclude.add(new AbstractMap.SimpleImmutableEntry<>(groupId, artifactId));
    }

    public List<Repository> getRepositories() {
        return repositories;
    }

    public List<Dependency> getDependencies() {
        return dependencies;
    }

    public List<Map.Entry<String, String>> getExclude() {
        return exclude;
    }

    public MavenRepositoryFetcher newFetcher(File saveTo) {
        return new MavenRepositoryFetcher(this, saveTo);
    }
}
