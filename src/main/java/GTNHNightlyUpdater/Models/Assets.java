package GTNHNightlyUpdater.Models;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.NonNull;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Assets {

    @Data
    public static final class Asset {
        private final List<Mod> mods;
        @SerializedName("latest_nightly")
        private final int latestNightly;
        @SerializedName("latest_successful_nightly")
        private final int latestSuccessfulNightly;
        private transient String configTag;
    }

    @Data
    public static final class Mod {
        private final String name;
        private String side;
        private final String source;
        @SerializedName("latest_version")
        private String latestVersion;
        @NonNull
        private final List<Version> versions;
    }

    @Data
    public static final class Version {
        @SerializedName("filename")
        private String githubName;
        @SerializedName("version_tag")
        private final String version;
        @SerializedName("download_url")
        private String downloadUrl;
        @SerializedName("browser_download_url")
        private String browserDownloadUrl;
        private transient String fileName;
        private transient Path cachePath;
        private transient List<Path> extraAssets = new ArrayList<>();
    }
}
