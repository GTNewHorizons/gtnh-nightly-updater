package GTNHNightlyUpdater;

import GTNHNightlyUpdater.Models.Assets;
import GTNHNightlyUpdater.Models.MavenSearch;
import com.google.gson.internal.LinkedTreeMap;
import lombok.Cleanup;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.apache.commons.io.FilenameUtils;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Log4j2(topic = "GTNHNightlyUpdater")
public class Updater {

    private final boolean useLatest;

    public Updater(boolean useLatest) {
        this.useLatest = useLatest;
    }

    void addLocalAssets(Assets.Asset assets, Path localAssets) throws IOException {
        val lines = Files.readAllLines(localAssets);
        for (val line : lines) {
            val split = line.split("\\|");
            val modName = split[0].trim();
            val side = split.length > 1 ? split[1].trim() : "BOTH";
            Assets.Mod mod = new Assets.Mod(modName, side, new ArrayList<>());
            assets.getMods().add(mod);
        }
    }

    void updateModpackMods(Assets.Asset assets, Path modCacheDir, Set<String> modExclusions, Main.Options.Instance.InstanceConfig instanceConfig) throws IOException {
        var minecraftModsDir = instanceConfig.getMinecraftDir().resolve("mods");
        val packMods = this.gatherExistingMods(minecraftModsDir);
        log.info("Updating modpack jars");

        for (val mod : assets.getMods()) {
            if (mod.getVersions() == null || mod.getVersions().isEmpty()) {
                continue;
            }
            if (mod.getSide() != null && mod.getSide().equals("NONE")) {
                for (val version : mod.getVersions()) {
                    if (packMods.containsKey(version.getFilename().toLowerCase())) {
                        log.info("\tDeleting mod with side of NONE: {} - {}", mod.getName(), version.getFilename());
                        Files.deleteIfExists(packMods.get(version.getFilename().toLowerCase()));
                        packMods.remove(version.getFilename().toLowerCase());
                    }
                }
                continue;
            }
            // side being null == BOTH
            if (mod.getSide() != null && !(mod.getSide().equalsIgnoreCase(String.valueOf(instanceConfig.side)) || mod.getSide().equalsIgnoreCase("BOTH"))) {
                continue;
            }

            if (modExclusions.contains(mod.getName())) {
                for (val version : mod.getVersions()) {
                    if (packMods.containsKey(version.getFilename().toLowerCase())) {
                        log.info("\tDeleting excluded mod: {} - {}", mod.getName(), version.getFilename());
                        Files.deleteIfExists(packMods.get(version.getFilename().toLowerCase()));
                        packMods.remove(version.getFilename().toLowerCase());
                        break;
                    } else if (version.getMavenFilename() != null && packMods.containsKey(version.getMavenFilename().toLowerCase())) {
                        log.info("\tDeleting excluded mod: {} - {}", mod.getName(), version.getMavenFilename());
                        Files.deleteIfExists(packMods.get(version.getMavenFilename().toLowerCase()));
                        packMods.remove(version.getMavenFilename().toLowerCase());
                        break;
                    }
                }
                log.info("\tSkipping {} due to exclusion", mod.getName());
                continue;
            }

            Assets.Version modVersionToUse = this.useLatest
                    ? mod.getVersions().getLast()
                    : mod.getVersions().stream()
                    .filter(v -> v.getVersion().equals(mod.getLatestVersion()))
                    .findFirst()
                    .orElse(null);

            if (modVersionToUse == null) {
                log.warn("Unable to determine mod version for {}", mod.getName());
                continue;
            }

            if (packMods.containsKey(modVersionToUse.getFilename().toLowerCase())) {
                continue;
            }
            if (modVersionToUse.getMavenFilename() != null && packMods.containsKey(modVersionToUse.getMavenFilename().toLowerCase())) {
                continue;
            }

            String newModFileName = modVersionToUse.getFilename();

            if (Files.notExists(modCacheDir.resolve(newModFileName))) {
                log.warn("Skipping {} - File not found: '{}'", mod.getName(), modCacheDir.resolve(newModFileName));
                continue;
            }

            String oldFileName = null;
            // check for old nightly version
            for (val version : mod.getVersions()) {
                if (packMods.containsKey(version.getFilename().toLowerCase())) {
                    Files.deleteIfExists(packMods.get(version.getFilename().toLowerCase()));
                    packMods.remove(version.getFilename().toLowerCase());
                    oldFileName = version.getFilename();
                    break;
                } else if (version.getMavenFilename() != null && packMods.containsKey(version.getMavenFilename().toLowerCase())) {
                    Files.deleteIfExists(packMods.get(version.getMavenFilename().toLowerCase()));
                    packMods.remove(version.getMavenFilename().toLowerCase());
                    oldFileName = version.getMavenFilename();
                    break;
                }
            }

            if (packMods.containsKey(modVersionToUse.getFilename().toLowerCase())) {
                continue;
            }

            if (oldFileName != null) {
                log.info("\tUpgrading {} - {} -> {}", mod.getName(), oldFileName, newModFileName);
            } else {
                log.info("\tNew Mod {} - {}", mod.getName(), newModFileName);
            }

            if (instanceConfig.isUseSymlinks()) {
                Files.createSymbolicLink(minecraftModsDir.resolve(newModFileName), modCacheDir.resolve(newModFileName));
            } else {
                Files.copy(modCacheDir.resolve(newModFileName), minecraftModsDir.resolve(newModFileName));
            }
            packMods.put(newModFileName, minecraftModsDir.resolve(newModFileName));
        }
    }

    void updateModsFromMaven(Assets.Asset asset) throws IOException, InterruptedException {
        log.info("Getting mod versions from maven");
        @Cleanup HttpClient client = HttpClient.newHttpClient();

        for (val mod : asset.getMods()) {
            // null source = our maven
            if (mod.getSource() != null || ((mod.getSide() != null && mod.getSide().equals("NONE")))) {
                continue;
            }

            log.info("\t{}", mod.getName());

            String url = String.format(
                    "https://nexus.gtnewhorizons.com/service/rest/v1/search/assets?&sort=version&repository=public&name=%s&maven.extension=jar&maven.classifier",
                    mod.getName()
            );
            var req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();
            var resp = client.send(req, HttpResponse.BodyHandlers.ofString());

            val mavenIndex = JsonParser.parse(resp.body(), MavenSearch.Index.class);
            if (mavenIndex == null) {
                log.warn("Unable to parse maven response for {}", mod.getName());
                continue;
            }
            var continuationToken = mavenIndex.continuationToken();
            while (continuationToken != null) {
                req = HttpRequest.newBuilder()
                        .uri(URI.create(url + "&continuationToken=" + continuationToken))
                        .GET()
                        .build();
                resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                val tempMavenIndex = JsonParser.parse(resp.body(), MavenSearch.Index.class);
                mavenIndex.items().addAll(tempMavenIndex.items());
                continuationToken = tempMavenIndex.continuationToken();
            }

            val versions = mavenIndex.items();
            if (versions.size() == 0) {
                log.warn("Unable to parse maven versions for {}", mod.getName());
                continue;
            }

            versions.sort(Comparator.comparing(MavenSearch.Item::lastModified));
            // Update or add versions
            for (val version : versions) {
                String versionString = version.maven2().version();
                String mavenFilename = FilenameUtils.getName(version.downloadUrl());

                // Check if the version already exists
                val existingVersion = mod.getVersions().stream()
                        .filter(v -> v.getVersion().equals(versionString))
                        .findFirst();

                if (existingVersion.isPresent()) {
                    existingVersion.get().setMavenFilename(mavenFilename);
                    existingVersion.get().setDownloadUrl(version.downloadUrl());
                } else {
                    Assets.Version newVersion = new Assets.Version(
                            mavenFilename,
                            versionString.endsWith("-pre"),
                            versionString
                    );
                    newVersion.setDownloadUrl(version.downloadUrl());
                    newVersion.setMavenFilename(mavenFilename);
                    mod.getVersions().add(newVersion);
                }
            }

            // edge cases for mods
            // reason: 0.55 -> 0.6+
            if (mod.getName().equals("BlockLimiter") || mod.getName().equals("oauth")) {
                mod.getVersions().removeIf(v -> v.getFilename().contains("-1.7.10-"));
            }

            if (mod.getVersions() != null) {
                mod.getVersions().sort(Comparator.comparing(v -> new DefaultArtifactVersion(v.getVersion())));
            }
        }
    }


    void cacheMods(Assets.Asset asset, Set<String> modExclusions, Path modCacheDir) throws IOException, InterruptedException {
        log.info("Caching nightly mods");
        @Cleanup HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
        for (val mod : asset.getMods()) {
            if ((mod.getSide() != null && mod.getSide().equals("NONE")) || mod.getVersions().isEmpty()) {
                continue;
            }

            if (modExclusions.contains(mod.getName())) {
                continue;
            }

            Assets.Version modVersionToUse;
            if (this.useLatest && mod.getSource() == null) {
                modVersionToUse = mod.getVersions().getLast();
            } else {
                val nightlyMod = mod.getVersions().stream().filter(v -> v.getVersion().equals(mod.getLatestVersion())).findFirst();
                if (nightlyMod.isPresent()) {
                    modVersionToUse = nightlyMod.get();
                } else {
                    log.warn("Unable to find nightly version of {}: {}", mod.getName(), mod.getLatestVersion());
                    continue;
                }
            }

            if (Files.exists(modCacheDir.resolve(modVersionToUse.getFilename())) || (modVersionToUse.getMavenFilename() != null && Files.exists(modCacheDir.resolve(modVersionToUse.getMavenFilename())))) {
                continue;
            }

            Path targetPath = modCacheDir.resolve(modVersionToUse.getFilename());

            String downloadURL;
            if (this.useLatest || mod.getSource() != null) {
                downloadURL = modVersionToUse.getDownloadUrl();
            } else {
                downloadURL = String.format(
                        "https://nexus.gtnewhorizons.com/service/rest/v1/search/assets/download?repository=public&name=%s&maven.extension=jar&maven.classifier&version=%s",
                        mod.getName(),
                        modVersionToUse.getVersion()
                );
            }


            if (Files.exists(targetPath)) {
                continue;
            }

            log.info("\t{}", mod.getName());
            val request = HttpRequest.newBuilder()
                    .uri(URI.create(downloadURL))
                    .GET()
                    .build();
            val response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (!(response.statusCode() == 200 || response.statusCode() == 302)) {
                log.warn("Failed to fetch jar: {} - {}", downloadURL, response.statusCode());
                continue;
            }
            Files.write(targetPath, response.body());
        }

    }

    Assets.Asset fetchDAXXLAssets() throws IOException, InterruptedException {
        log.info("Fetching latest gtnh-assets.json");

        @Cleanup HttpClient client = HttpClient.newHttpClient();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://raw.githubusercontent.com/GTNewHorizons/DreamAssemblerXXL/refs/heads/master/gtnh-assets.json"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Failed to fetch assets file: HTTP " + response.statusCode());
        }
        val asset = JsonParser.parse(response.body(), Assets.Asset.class);

        request = HttpRequest.newBuilder()
                .uri(URI.create("https://raw.githubusercontent.com/GTNewHorizons/DreamAssemblerXXL/refs/heads/master/releases/manifests/nightly.json"))
                .GET()
                .build();
        response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Failed to fetch manifest file: HTTP " + response.statusCode());
        }
        val map = (LinkedTreeMap<String, Object>) JsonParser.parse(response.body(), Map.class);

        asset.setConfigTag(map.get("config").toString());

        // set all sides to none; manifest will set the side
        for (val mod : asset.getMods()) {
            mod.setSide("NONE");
        }

        for (val mod : ((LinkedTreeMap<String, LinkedTreeMap<String, String>>) map.get("github_mods")).entrySet()) {
            asset.getMods().stream().filter(m -> m.getName().equals(mod.getKey())).findFirst().ifPresent(m -> {
                m.setSide(mod.getValue().get("side"));
                m.setLatestVersion(mod.getValue().get("version"));
            });
        }

        for (val mod : ((LinkedTreeMap<String, LinkedTreeMap<String, String>>) map.get("external_mods")).entrySet()) {
            asset.getMods().stream().filter(m -> m.getName().equals(mod.getKey())).findFirst().ifPresent(m -> {
                m.setSide(mod.getValue().get("side"));
                m.setLatestVersion(mod.getValue().get("version"));
            });
        }

        return asset;
    }

    Map<String, Path> gatherExistingMods(Path minecraftModsDir) throws IOException {
        log.info("Gathering existing mods");

        return Files.list(minecraftModsDir)
                .filter(path -> path.toString().endsWith(".jar"))
                .collect(Collectors.toMap(
                        path -> path.getFileName().toString().toLowerCase(),
                        path -> path
                ));
    }
}
