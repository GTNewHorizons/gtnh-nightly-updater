package GTNHNightlyUpdater.Models;

import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

import java.util.Date;
import java.util.List;

public class MavenSearch {
    public record Index(List<Item> items, String continuationToken) {
    }

    public record Item(
            String downloadUrl,
            Date lastModified,
            Maven2 maven2
    ) {
    }

    public record Maven2(String version, String artifactId){}
}

