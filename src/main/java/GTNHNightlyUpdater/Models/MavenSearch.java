package GTNHNightlyUpdater.Models;

import java.util.Date;
import java.util.List;

public class MavenSearch {
    public record Index(List<Item> items) {
    }

    public record Item(
            String downloadUrl,
            Date lastModified,
            Maven2 maven2
    ) {
    }

    public record Maven2(String version, String artifactId){}
}

