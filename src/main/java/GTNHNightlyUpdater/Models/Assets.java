package GTNHNightlyUpdater.Models;

import com.google.gson.annotations.SerializedName;

import java.util.Date;
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

    public static final class Mod {
        private final String name;
        private String side;
        private final String source;
        @SerializedName("latest_version")
        private String latestVersion;
        private final List<Version> versions;

        public Mod(
                String name,


                //if side is null, then side is BOTH according to DAXXL
                String side,
                String source,
                String latestVersion,
                List<Version> versions
        ) {
            this.name = name;
            this.side = side;
            this.source = source;
            this.latestVersion = latestVersion;
            this.versions = versions;
        }

        public String name() {
            return name;
        }

        public String side() {
            return side;
        }

        public void side(String val) {
            side = val;
        }

        public String source() {
            return source;
        }

        @SerializedName("latest_version")
        public String latestVersion() {
            return latestVersion;
        }

        public void latestVersion(String value) {
            latestVersion = value;
        }

        public List<Version> versions() {
            return versions;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (Mod) obj;
            return Objects.equals(this.name, that.name) &&
                    Objects.equals(this.side, that.side) &&
                    Objects.equals(this.source, that.source) &&
                    Objects.equals(this.latestVersion, that.latestVersion) &&
                    Objects.equals(this.versions, that.versions);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, side, source, latestVersion, versions);
        }

        @Override
        public String toString() {
            return "Mod[" +
                    "name=" + name + ", " +
                    "side=" + side + ", " +
                    "source=" + source + ", " +
                    "latestVersion=" + latestVersion + ", " +
                    "versions=" + versions + ']';
        }

    }

    // not a record due to added mutable field
    public static final class Version {
        private final String filename;
        private final boolean prerelease;
        @SerializedName("version_tag")
        private final String version;
        @SerializedName("download_url")
        private String downloadUrl;

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

        public void downloadUrl(String value) {
            downloadUrl = value;
        }

        public String mavenFilename() {
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
