package GTNHNightlyUpdater;

import GTNHNightlyUpdater.Models.Assets;
import lombok.Cleanup;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import lombok.val;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Log4j2
public class Main {

    public static class Config {
        public static Path minecraftDir;
        public static Path modsDir;
        public static Path cacheDir;
        public static boolean useLatest;

        public static boolean useSymlinks;
    }

    // todo: error handling
    @SneakyThrows
    public static void main(String[] args) {
        setupConfig(args);

        val assets = fetchAssets();
        val packMods = gatherExistingMods();
    }

    static void setupConfig(String[] args) throws IOException {
        if (args.length == 0) {
            log.fatal("Usage: java gtnh-nightly-updater <Minecraft directory> [--latest] [--symlink]");
            System.exit(1);
        }

        Config.minecraftDir = Path.of(args[0]);
        if (!Files.exists(Config.minecraftDir)) {
            log.fatal("Minecraft Directory not found: {}", Config.minecraftDir.toString());
            System.exit(1);
        }
        Config.modsDir = Path.of(Config.minecraftDir.toString(), "mods");
        if (!Files.exists(Config.modsDir)) {
            log.fatal("Mods Directory not found: {}", Config.modsDir.toString());
            System.exit(1);
        }

        val osName = System.getProperty("os.name").toLowerCase();
        Path cacheDir;
        if (osName.contains("win")){
            cacheDir = Path.of(System.getenv("LOCALAPPDATA"));
        } else if (osName.contains("mac")) {
            cacheDir = Path.of(System.getProperty("user.home"), "Library", "Caches");
        } else {
            cacheDir = Path.of(System.getenv("XDG_CACHE_HOME"));
            if (Files.notExists(cacheDir)){
                cacheDir = Path.of(System.getProperty("user.home"), ".cache");
            }
        }
        Config.cacheDir = Path.of(cacheDir.toString(), "gtnh-nightly-updater", "mods");
        Files.createDirectory(cacheDir);

        Config.useLatest = Arrays.asList(args).contains("--latest");
        Config.useSymlinks = Arrays.asList(args).contains("--symlink");
    }

    static void cacheMods(Assets.GTNHAsset asset) {
        log.debug("Caching nightly mods");


        @Cleanup ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (val mod : asset.mods()){
            if (mod.side().equals("NONE") || mod.versions().isEmpty()) {
                continue;
            }


        }
    }

    static Assets.GTNHAsset fetchAssets() throws IOException, InterruptedException {
        log.debug("Fetching latest gtnh-assets.json");

        @Cleanup HttpClient client = HttpClient.newHttpClient();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://raw.githubusercontent.com/GTNewHorizons/DreamAssemblerXXL/refs/heads/master/gtnh-assets.json"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Failed to fetch assets file: HTTP " + response.statusCode());
        }

        return JsonParser.parse(response.body(), Assets.GTNHAsset.class);
    }

    static Map<String, Path> gatherExistingMods() throws IOException {
        log.info("Gathering existing mods");

        return Files.list(Config.modsDir)
                .filter(path -> path.toString().endsWith(".jar"))
                .collect(Collectors.toMap(
                        path -> path.getFileName().toString(),
                        path -> path
                ));
    }

}
