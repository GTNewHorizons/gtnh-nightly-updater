package GTNHNightlyUpdater;

import GTNHNightlyUpdater.Models.Assets;
import GTNHNightlyUpdater.Models.MavenSearch;
import com.google.gson.internal.LinkedTreeMap;
import lombok.Cleanup;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Log4j2(topic = "GTNHNightlyUpdater")
public class Main {

    public static class Config {
        public static String side;
        public static Path minecraftDir;
        public static Path minecraftModsDir;
        public static Path modCacheDir;

        // used to store any additional mods; foramt is `modName|side`; requires useLatest
        public static Path localAssetFile;

        // used to store any mods to remove; takes mod name found in assets file
        public static Set<String> modExclusions;
        public static boolean useLatest;
        public static boolean useSymlinks;
    }

    // todo: error handling
    public static void main(String[] args) throws Throwable {
        parseArgs(args);

        val assets = fetchDAXXLAssets();
        if (Config.useLatest) {
            if (Files.exists(Config.localAssetFile)){
                addLocalAssets(assets);
            }
            updateModsFromMaven(assets);
        }
        cacheMods(assets);
        val packMods = gatherExistingMods();
        updateModpackMods(assets, packMods);
    }

    private static void addLocalAssets(Assets.Asset assets) throws IOException {
        val lines = Files.readAllLines(Config.localAssetFile);
        for (val line: lines){
            val split = line.split("\\|");
            val modName = split[0].trim();
            val side = split.length > 1 ? split[1].trim() : "BOTH";
            Assets.Mod mod = new Assets.Mod(modName, side, null, "", new ArrayList<>());
            assets.mods().add(mod);
        }
    }

    private static void parseArgs(String[] args) throws ParseException, IOException {
        Options opts = new Options();
        opts.addRequiredOption("s", "side", true, "Side of minecraft directory; CLIENT or SERVER");
        opts.addRequiredOption("m", "minecraft", true, "Target minecraft directory");
        opts.addOption("l", "latest", false, "Use latest github release instead of nightly version");
        opts.addOption("S", "symlinks", false, "Use symlinks instead of copying mods from the cache; Linux only");

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(opts, args);

        Config.side = cmd.getOptionValue("side").toUpperCase();
        if (!(Config.side.equals("SERVER") || Config.side.equals("CLIENT"))) {
            log.fatal("Side must be CLIENT or SERVER");
            System.exit(1);
        }

        Config.minecraftDir = Path.of(cmd.getOptionValue("minecraft"));
        Config.minecraftModsDir = Path.of(Config.minecraftDir.toString(), "mods");
        if (!Files.exists(Config.minecraftModsDir)) {
            log.fatal("Mods Directory not found: {}", Config.minecraftModsDir.toString());
            System.exit(1);
        }

        val osName = System.getProperty("os.name").toLowerCase();
        Path cacheDir;
        if (osName.contains("win")) {
            cacheDir = Path.of(System.getenv("LOCALAPPDATA"));
        } else if (osName.contains("mac")) {
            cacheDir = Path.of(System.getProperty("user.home"), "Library", "Caches");
        } else {
            cacheDir = Path.of(System.getenv("XDG_CACHE_HOME"));
            if (Files.notExists(cacheDir)) {
                cacheDir = Path.of(System.getProperty("user.home"), ".cache");
            }
        }
        Config.modCacheDir = cacheDir.resolve("gtnh-nightly-updater").resolve("mods");
        if (Files.notExists(Config.modCacheDir.getParent())) {
            Files.createDirectory(Config.modCacheDir.getParent());
        }
        if (Files.notExists(Config.modCacheDir)) {
            Files.createDirectory(Config.modCacheDir);
        }

        Config.localAssetFile = Config.modCacheDir.resolveSibling("local-assets.txt");
        if (Files.exists(Config.modCacheDir.resolveSibling("mod-exclusions.txt"))) {
            Config.modExclusions = new HashSet<>(Files.readAllLines(Config.modCacheDir.resolveSibling("mod-exclusions.txt")));
        }

        Config.useLatest = cmd.hasOption("latest");
        Config.useSymlinks = cmd.hasOption("symlinks");
    }

    private static void updateModpackMods(Assets.Asset assets, Map<String, Path> packMods) throws IOException {
        log.info("Updating modpack jars");

        for (val mod : assets.mods()) {
            if (mod.versions().isEmpty()) {
                continue;
            }
            if (mod.side() != null && mod.side().equals("NONE")) {
                for (val version : mod.versions()) {
                    if (packMods.containsKey(version.filename().toLowerCase())) {
                        log.info("\tDeleting mod with side of NONE: {} - {}", mod.name(), version.filename());
                        Files.deleteIfExists(packMods.get(version.filename()));
                    }
                }
                continue;
            }
            // side being null == BOTH
            if (mod.side() != null && !(mod.side().equalsIgnoreCase(Config.side) || mod.side().equalsIgnoreCase("BOTH"))) {
                continue;
            }

            if (Config.modExclusions != null && Config.modExclusions.contains(mod.name())) {
                log.info("\tSkipping {} due to exclusion", mod.name());
                continue;
            }

            Assets.Version modVersionToUse = Config.useLatest
                    ? mod.versions().getLast()
                    : mod.versions().stream()
                    .filter(v -> v.version().equals(mod.latestVersion()))
                    .findFirst()
                    .orElse(null);

            if (modVersionToUse == null) {
                log.warn("Unable to determine mod version for {}", mod.name());
                continue;
            }

            String newModFileName = modVersionToUse.filename();
            if (packMods.containsKey(newModFileName.toLowerCase())) {
                continue;
            }

            String oldFileName = null;
            // check for old nightly version
            for (val version : mod.versions()) {
                if (packMods.containsKey(version.filename().toLowerCase())) {
                    Files.deleteIfExists(packMods.get(version.filename().toLowerCase()));
                    oldFileName = version.filename();
                    break;
                } else if (version.mavenFilename() != null && packMods.containsKey(version.mavenFilename().toLowerCase())) {
                    Files.deleteIfExists(packMods.get(version.mavenFilename().toLowerCase()));
                    oldFileName = version.mavenFilename();
                    break;
                }
            }

            if (packMods.containsKey(modVersionToUse.filename().toLowerCase())){
                continue;
            }

            if (oldFileName != null) {
                log.info("\tUpgrading {} - {} -> {}", mod.name(), oldFileName, newModFileName);
            } else {
                log.info("\tNew Mod {} - {}", mod.name(), newModFileName);
            }

            if (Config.useSymlinks) {
                Files.createSymbolicLink(Config.minecraftModsDir.resolve(newModFileName), Config.modCacheDir.resolve(newModFileName));
            } else {
                Files.copy(Config.modCacheDir.resolve(newModFileName), Config.minecraftModsDir.resolve(newModFileName));
            }
        }
    }

    static void updateModsFromMaven(Assets.Asset asset) throws IOException, InterruptedException {
        log.info("Getting mod versions from maven");
        @Cleanup HttpClient client = HttpClient.newHttpClient();

        for (val mod : asset.mods()) {
            // null source = our maven
            if (mod.source() != null || ((mod.side() != null && mod.side().equals("NONE")))) {
                continue;
            }

            log.info("\t{}", mod.name());

            String url = String.format(
                    "https://nexus.gtnewhorizons.com/service/rest/v1/search/assets?&sort=version&repository=public&group=com.github.GTNewHorizons&name=%s&maven.extension=jar&maven.classifier",
                    mod.name()
            );
            var req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();
            var resp = client.send(req, HttpResponse.BodyHandlers.ofString());

            val mavenIndex = JsonParser.parse(resp.body(), MavenSearch.Index.class);
            if (mavenIndex == null) {
                log.warn("Unable to parse maven response for {}", mod.name());
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
                log.warn("Unable to parse maven versions for {}", mod.name());
                continue;
            }

            versions.sort(Comparator.comparing(MavenSearch.Item::lastModified));
            // Update or add versions
            for (val version : versions) {
                String versionString = version.maven2().version();
                String mavenFilename = Path.of(version.downloadUrl()).getFileName().toString();

                // Check if the version already exists
                val existingVersion = mod.versions().stream()
                        .filter(v -> v.version().equals(versionString))
                        .findFirst();

                if (existingVersion.isPresent()) {
                    existingVersion.get().mavenFilename(mavenFilename);
                    existingVersion.get().downloadUrl(version.downloadUrl());
                } else {
                    Assets.Version newVersion = new Assets.Version(
                            mavenFilename,
                            versionString.endsWith("-pre"),
                            versionString,
                            version.downloadUrl()
                    );
                    newVersion.mavenFilename(mavenFilename);
                    mod.versions().add(newVersion);
                }
            }

            // edge cases for mods
            // reason: 0.55 -> 0.6+
            if (mod.name().equals("BlockLimiter")) {
                mod.versions().removeIf(v -> v.filename().contains("-1.7.10-"));
            }

            if (mod.versions() != null) {
                mod.versions().sort(Comparator.comparing(v -> new DefaultArtifactVersion(v.version())));
            }
        }


    }


    static void cacheMods(Assets.Asset asset) throws IOException, InterruptedException {
        log.info("Caching nightly mods");
        @Cleanup HttpClient client = HttpClient.newHttpClient();
        for (val mod : asset.mods()) {
            if ((mod.side() != null && mod.side().equals("NONE")) || mod.versions().isEmpty()) {
                continue;
            }

            Assets.Version modVersionToUse;
            if (Config.useLatest && mod.source() == null) {
                modVersionToUse = mod.versions().getLast();
            } else {
                val nightlyMod = mod.versions().stream().filter(v -> v.version().equals(mod.latestVersion())).findFirst();
                if (nightlyMod.isPresent()) {
                    modVersionToUse = nightlyMod.get();
                } else {
                    log.warn("Unable to find nightly version of {}: {}", mod.name(), mod.latestVersion());
                    continue;
                }
            }

            Path targetPath = Config.modCacheDir.resolve(modVersionToUse.filename());

            String downloadURL;
            if (Config.useLatest || mod.source() != null) {
                downloadURL = modVersionToUse.downloadUrl();
            } else {
                downloadURL = String.format(
                        "https://nexus.gtnewhorizons.com/service/rest/v1/search/assets/download?repository=public&group=com.github.GTNewHorizons&name=%s&maven.extension=jar&maven.classifier&version=%s",
                        mod.name(),
                        modVersionToUse.version()
                );
            }


            if (Files.exists(targetPath)) {
                continue;
            }

            log.info("\t{}", mod.name());
            val request = HttpRequest.newBuilder()
                    .uri(URI.create(downloadURL))
                    .GET()
                    .build();
            val response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (!(response.statusCode() == 200 || response.statusCode() == 302)) {
                throw new IOException(String.format("Failed to fetch jar: %s - %d", downloadURL, response.statusCode()));
            }
            Files.write(targetPath, response.body());
        }

    }

    static Assets.Asset fetchDAXXLAssets() throws IOException, InterruptedException {
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

        // set all sides to none; manifest will set the side
        for (val mod : asset.mods()) {
            mod.side("NONE");
        }

        for (val mod : ((LinkedTreeMap<String, LinkedTreeMap<String, String>>) map.get("github_mods")).entrySet()) {
            asset.mods().stream().filter(m -> m.name().equals(mod.getKey())).findFirst().ifPresent(m -> {
                m.side(mod.getValue().get("side"));
                m.latestVersion(mod.getValue().get("version"));
            });
        }

        for (val mod : ((LinkedTreeMap<String, LinkedTreeMap<String, String>>) map.get("external_mods")).entrySet()) {
            asset.mods().stream().filter(m -> m.name().equals(mod.getKey())).findFirst().ifPresent(m -> {
                m.side(mod.getValue().get("side"));
                m.latestVersion(mod.getValue().get("version"));
            });
        }

        return asset;
    }

    static Map<String, Path> gatherExistingMods() throws IOException {
        log.info("Gathering existing mods");

        return Files.list(Config.minecraftModsDir)
                .filter(path -> path.toString().endsWith(".jar"))
                .collect(Collectors.toMap(
                        path -> path.getFileName().toString().toLowerCase(),
                        path -> path
                ));
    }

}
