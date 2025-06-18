package GTNHNightlyUpdater;

import GTNHNightlyUpdater.Models.Assets;
import GTNHNightlyUpdater.Models.MavenSearch;
import com.google.gson.internal.LinkedTreeMap;
import lombok.Cleanup;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Log4j2(topic = "GTNHNightlyUpdater")
public class Updater {
    private final Main.Options options;

    public Updater(Main.Options options) {
        this.options = options;
    }

    void addLocalAssets(Assets.Asset assets, Path localAssets) throws IOException {
        val lines = Files.readAllLines(localAssets);
        for (val line : lines) {
            val split = line.split("\\|");
            val modName = split[0].trim();
            val side = split.length > 1 ? split[1].trim() : "BOTH";
            Assets.Mod mod = new Assets.Mod(modName, null, new ArrayList<>());
            mod.setSide(side);
            try {
                updateModFromMaven(mod);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            assets.getMods().add(mod);
        }
    }

    void updateModpackMods(Assets.Asset assets, Path modCacheDir, Set<String> modExclusions, Main.Options.Instance.InstanceConfig instanceConfig) throws IOException {
        var minecraftModsDir = instanceConfig.getMinecraftDir().resolve("mods");
        val packMods = this.gatherExistingMods(minecraftModsDir);
        log.info("Updating modpack jars");

        val keptMods = new TreeSet<String>();
        var mods = assets.getMods();
        mods.sort(Comparator.comparing(a -> a.getName().toLowerCase()));
        for (val mod : mods.reversed()) {
            if (mod.getVersions() == null || mod.getVersions().isEmpty()) {
                continue;
            }
            if (mod.getSide() != null && mod.getSide().equals("NONE")) {
                for (val version : mod.getVersions()) {
                    if (packMods.containsKey(version.getFilename())) {
                        log.info("\tDeleting mod with side of NONE: {} - {}", mod.getName(), version.getFilename());
                        Files.deleteIfExists(packMods.get(version.getFilename()));
                        packMods.remove(version.getFilename());
                    }
                }
                continue;
            }

            // side being null == BOTH
            val modSide = mod.getSide() != null ? mod.getSide().split("_")[0] : "BOTH";
            if (mod.getSide() != null && !(modSide.equalsIgnoreCase(String.valueOf(instanceConfig.side)) || modSide.equalsIgnoreCase("BOTH"))) {
                continue;
            }

            if (modExclusions.contains(mod.getName())) {
                for (val version : mod.getVersions()) {
                    if (packMods.containsKey(version.getFilename())) {
                        log.info("\tDeleting excluded mod: {} - {}", mod.getName(), version.getFilename());
                        Files.deleteIfExists(packMods.get(version.getFilename()));
                        packMods.remove(version.getFilename());
                        break;
                    }
                }
                continue;
            }

            Assets.Version modVersionToUse = mod.getVersions().stream()
                    .filter(v -> v.getVersion().equals(mod.getLatestVersion()))
                    .findFirst()
                    .orElse(null);

            if (modVersionToUse == null) {
                log.warn("\tUnable to determine mod version for {}", mod.getName());
                continue;
            }

            if (modVersionToUse.getCachePath() == null) {
                log.warn("\tUnable to get cached path for {}", mod.getName());
                continue;
            }

            String newModFileName = modVersionToUse.getCachePath().getFileName().toString();

            Path modCacheLocation = modVersionToUse.getCachePath();

            if (Files.notExists(modCacheLocation)) {
                log.warn("\tSkipping {} - File not found: '{}'", mod.getName(), modCacheLocation);
                continue;
            }

            String oldFileName = null;

            // delete older versions
            for (val oldVersion : mod.getVersions()) {
                if (oldVersion.getVersion().equals(modVersionToUse.getVersion())) {
                    continue;
                }
                String toRemove = oldVersion.getFilename();
                for (Iterator<Map.Entry<String, Path>> iterator = packMods.entrySet().iterator(); iterator.hasNext(); ) {
                    Map.Entry<String, Path> entry = iterator.next();
                    if (entry.getValue().getFileName().toString().equals(toRemove) && !keptMods.contains(entry.getKey())) {
                        Files.deleteIfExists(entry.getValue());
                        oldFileName = oldVersion.getFilename();
                        iterator.remove();
                    }
                }
            }

            // delete non-matching versions to handle local builds (usually -pre, but not name changes)
            Pattern versionPattern = Pattern.compile(escapeQuotes(newModFileName).replace(escapeQuotes(modVersionToUse.getVersion()), ".*"), Pattern.CASE_INSENSITIVE);

            for (Iterator<Map.Entry<String, Path>> iterator = packMods.entrySet().iterator(); iterator.hasNext(); ) {
                Map.Entry<String, Path> entry = iterator.next();
                if (!entry.getKey().toString().equals(newModFileName) && versionPattern.matcher(entry.getKey()).matches() && !keptMods.contains(entry.getKey())) {
                    Files.deleteIfExists(entry.getValue());
                    oldFileName = entry.getValue().getFileName().toString();
                    iterator.remove();
                }
            }

            keptMods.add(newModFileName);

            Path modDest = minecraftModsDir.resolve(newModFileName);

            if (Files.exists(modDest)) continue;

            if (oldFileName != null) {
                log.info("\tUpgrading {} - {} -> {}", mod.getName(), oldFileName, newModFileName);
            } else {
                log.info("\tNew Mod {} - {}", mod.getName(), newModFileName);
            }

            if (instanceConfig.isUseSymlinks()) {
                Files.createSymbolicLink(modDest, modCacheLocation);
            } else {
                Files.copy(modCacheLocation, modDest);
            }
            packMods.put(newModFileName, modDest);

            var extraAssets = modVersionToUse.getExtraAssets();
            if (extraAssets != null && extraAssets.size() > 0) {
                if (mod.getName().equalsIgnoreCase("lwjgl3ify")) {
                    Path rootMinecraftDir = instanceConfig.getMinecraftDir();

                    if (instanceConfig.side == Main.Options.Instance.InstanceConfig.Side.SERVER) {
                        val forgePatches = extraAssets.stream().filter(a -> a.getFileName().toString().endsWith("-forgePatches.jar")).findFirst();

                        if (forgePatches.isPresent()) {
                            val patchesFile = forgePatches.get();
                            modDest = rootMinecraftDir.resolve("lwjgl3ify-forgePatches.jar");
                            if (instanceConfig.isUseSymlinks()) {
                                Files.createSymbolicLink(modDest, patchesFile);
                            } else {
                                Files.copy(patchesFile, modDest, StandardCopyOption.REPLACE_EXISTING);
                            }
                        }
                    } else if (instanceConfig.side == Main.Options.Instance.InstanceConfig.Side.CLIENT) {
                        val zip = extraAssets.stream().filter(a -> a.getFileName().toString().endsWith("-multimc.zip")).findFirst();

                        if (zip.isPresent()) {
                            val zipFile = zip.get();

                            // should be up one
                            extractZip(zipFile, rootMinecraftDir.getParent());
                        }
                    }
                }
            }
        }
    }

    private void extractZip(Path zipPath, Path targetDir) throws IOException {
        // Ensure the target directory exists
        if (!Files.exists(targetDir)) {
            Files.createDirectories(targetDir);
        }

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath))) {
            ZipEntry entry;

            while ((entry = zis.getNextEntry()) != null) {
                Path resolvedPath = targetDir.resolve(entry.getName()).normalize();

                // Prevent Zip Slip vulnerability
                if (!resolvedPath.startsWith(targetDir)) {
                    throw new IOException("Entry is outside the target dir: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    if (Files.exists(resolvedPath)) {
                        FileUtils.deleteDirectory(resolvedPath.toFile());
                    }
                    Files.createDirectories(resolvedPath);
                } else {
                    // Ensure parent directories exist
                    if (!Files.exists(resolvedPath.getParent())) {
                        Files.createDirectories(resolvedPath.getParent());
                    }

                    // Overwrite file if it exists
                    try (OutputStream os = Files.newOutputStream(resolvedPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = zis.read(buffer)) != -1) {
                            os.write(buffer, 0, bytesRead);
                        }
                    }
                }

                zis.closeEntry();
            }
        }
    }

    void cacheMods(Assets.Asset asset, Set<String> modExclusions, Path modCacheDir) throws IOException, InterruptedException {
        log.info("Caching mods");
        @Cleanup HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
        for (val mod : asset.getMods()) {
            if ((mod.getSide() != null && mod.getSide().equals("NONE")) || mod.getVersions().isEmpty()) {
                continue;
            }

            if (modExclusions.contains(mod.getName())) {
                continue;
            }

            Assets.Version modVersionToUse;

            val nightlyMod = mod.getVersions().stream().filter(v -> v.getVersion().equals(mod.getLatestVersion())).findFirst();
            if (nightlyMod.isPresent()) {
                modVersionToUse = nightlyMod.get();
            } else {
                log.warn("\tUnable to find version of {}: {}", mod.getName(), mod.getLatestVersion());
                continue;
            }


            Path targetPath = modCacheDir.resolve(mod.getName().replaceAll("[<>:\"/\\\\|?*]", "")).resolve(modVersionToUse.getFilename());
            if (Files.notExists(targetPath.getParent())) {
                Files.createDirectory(targetPath.getParent());
            }

            if (Files.exists(modCacheDir.resolve(modVersionToUse.getFilename()))) {
                Files.move(modCacheDir.resolve(modVersionToUse.getFilename()), targetPath);
            }

            modVersionToUse.setCachePath(targetPath);

            if (Files.exists(targetPath)) {
                getExtraAssets(mod, modVersionToUse, client, targetPath);
                continue;
            }

            log.info("\t{}", mod.getName());

            String downloadURL;
            if (mod.getSource() != null) {
                downloadURL = modVersionToUse.getDownloadUrl().replace("/media.", "/mediafilez.");
            } else {
                downloadURL = String.format(
                        "https://nexus.gtnewhorizons.com/service/rest/v1/search/assets/download?repository=public&name=%s&maven.extension=jar&maven.classifier&version=%s",
                        mod.getName(),
                        modVersionToUse.getVersion()
                );
            }

            var downloadBytes = downloadFile(downloadURL, client);
            if (downloadBytes == null) continue;

            Files.write(targetPath, downloadBytes);

            getExtraAssets(mod, modVersionToUse, client, targetPath);
        }

    }

    private static void getExtraAssets(Assets.Mod mod, Assets.Version modVersionToUse, HttpClient client, Path targetPath) throws IOException, InterruptedException {
        String downloadURL;
        byte[] downloadBytes;
        if (mod.getName().equalsIgnoreCase("lwjgl3ify")) {
            if (modVersionToUse.getExtraAssets() == null) {
                modVersionToUse.setExtraAssets(new ArrayList<>());
            }

            targetPath = targetPath.resolveSibling(String.format("%s-%s-multimc.zip", mod.getName(), modVersionToUse.getVersion()));
            if (!Files.exists(targetPath)) {
                downloadURL = String.format(
                        "https://nexus.gtnewhorizons.com/service/rest/v1/search/assets/download?repository=public&name=%s&maven.extension=zip&maven.classifier=multimc&version=%s",
                        mod.getName(),
                        modVersionToUse.getVersion()
                );

                downloadBytes = downloadFile(downloadURL, client);
                if (downloadBytes == null) return;
                Files.write(targetPath, downloadBytes);
            }
            modVersionToUse.getExtraAssets().add(targetPath);

            targetPath = targetPath.resolveSibling(String.format("%s-%s-forgePatches.jar", mod.getName(), modVersionToUse.getVersion()));
            if (!Files.exists(targetPath)) {
                downloadURL = String.format(
                        "https://nexus.gtnewhorizons.com/service/rest/v1/search/assets/download?repository=public&name=%s&maven.extension=jar&maven.classifier=forgePatches&version=%s",
                        mod.getName(),
                        modVersionToUse.getVersion()
                );

                downloadBytes = downloadFile(downloadURL, client);
                if (downloadBytes == null) return;
                Files.write(targetPath, downloadBytes);
            }
            modVersionToUse.getExtraAssets().add(targetPath);
        }
    }

    private static byte[] downloadFile(String downloadURL, HttpClient client) throws IOException, InterruptedException {
        val request = HttpRequest.newBuilder()
                .uri(URI.create(downloadURL))
                .GET()
                .build();
        val response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (!(response.statusCode() == 200 || response.statusCode() == 302)) {
            log.warn("\tFailed to fetch jar: {} - {}", downloadURL, response.statusCode());
            return null;
        }
        return response.body();
    }

    void updateModFromMaven(Assets.Mod mod) throws IOException, InterruptedException {
        @Cleanup HttpClient client = HttpClient.newHttpClient();

        // null source = our maven
        if (mod.getSource() != null || ((mod.getSide() != null && mod.getSide().equals("NONE")))) {
            return;
        }

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
            return;
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
            return;
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
                existingVersion.get().setFilename(mavenFilename);
                existingVersion.get().setDownloadUrl(version.downloadUrl());
            } else {
                Assets.Version newVersion = new Assets.Version(
                        versionString.endsWith("-pre"),
                        versionString
                );
                newVersion.setDownloadUrl(version.downloadUrl());
                newVersion.setFilename(mavenFilename);
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

        if (mod.getVersions().size() > 0) {
            mod.setLatestVersion(mod.getVersions().getLast().getVersion());
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
                .uri(URI.create(String.format("https://raw.githubusercontent.com/GTNewHorizons/DreamAssemblerXXL/refs/heads/master/releases/manifests/%s.json", options.targetManifest.name().toLowerCase())))
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
                        path -> path.getFileName().toString(),
                        path -> path,
                        (existing, replacement) -> existing,
                        TreeMap::new
                ));
    }

    static String escapeChars = "\\.?![]{}()<>*+-=^$|";

    String escapeQuotes(String str) {
        if (str != null && str.length() > 0) {
            return str.replaceAll("[\\W]", "\\\\$0"); // \W designates non-word characters
        }
        return "";
    }
}
