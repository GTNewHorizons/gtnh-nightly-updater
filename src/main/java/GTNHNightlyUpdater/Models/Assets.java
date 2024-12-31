package GTNHNightlyUpdater.Models;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class Assets {

    // todo: configs
    public record GTNHAsset(
            List<GTNHMod> mods,
            @SerializedName("latest_nightly") int latestNightly,
            @SerializedName("latest_successful_nightly") int latestSuccessfulNightly
    ) {}

    public record GTNHMod(
            String name,
            String side,
            String source,
            @SerializedName("latest_version") String latestVersion,
            List<GTNHVersion> versions
    ) {}

    public record GTNHVersion(
            String filename,
            boolean prerelease,
            @SerializedName("version_tag") String version,
            @SerializedName("download_url") String downloadUrl
    ) {}
}
