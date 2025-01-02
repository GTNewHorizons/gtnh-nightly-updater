package GTNHNightlyUpdater.Models;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public class Assets {

    // todo: configs
    public record Asset(
            List<Mod> mods,
            @SerializedName("latest_nightly") int latestNightly,
            @SerializedName("latest_successful_nightly") int latestSuccessfulNightly
    ) {
    }

    public record Mod(
            String name,


            //if side is null, then side is BOTH according to DAXXL
            String side,
            String source,
            @SerializedName("latest_version") String latestVersion,
            List<Version> versions
    ) {
    }

    public static final class Version {
        private final String filename;
        private final boolean prerelease;
        @SerializedName("version_tag")
        private final String version;
        @SerializedName("download_url")
        private final String downloadUrl;

        private String mavenFilename;

        public Version(
                String filename,
                boolean prerelease,
                String version,
                String downloadUrl
        ) {
            this.filename = filename;
            this.prerelease = prerelease;
            this.version = version;
            this.downloadUrl = downloadUrl;
        }

        public String filename() {
            return filename;
        }

        public boolean prerelease() {
            return prerelease;
        }

        @SerializedName("version_tag")
        public String version() {
            return version;
        }

        @SerializedName("download_url")
        public String downloadUrl() {
            return downloadUrl;
        }

        public String mavenFilename(){
            return mavenFilename;
        }

        public void mavenFilename(String value) {
            mavenFilename = value;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (Version) obj;
            return Objects.equals(this.filename, that.filename) &&
                    this.prerelease == that.prerelease &&
                    Objects.equals(this.version, that.version) &&
                    Objects.equals(this.downloadUrl, that.downloadUrl);
        }

        @Override
        public int hashCode() {
            return Objects.hash(filename, prerelease, version, downloadUrl);
        }

        @Override
        public String toString() {
            return "Version[" +
                    "filename=" + filename + ", " +
                    "prerelease=" + prerelease + ", " +
                    "version=" + version + ", " +
                    "downloadUrl=" + downloadUrl + ']';
        }

        }
}
