package GTNHNightlyUpdater;

import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

@Log4j2(topic = "GTNHNightlyUpdater-Main")
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

        val assets = Updater.fetchDAXXLAssets();
        if (Config.useLatest) {
            if (Files.exists(Config.localAssetFile)) {
                Updater.addLocalAssets(assets);
            }
            Updater.updateModsFromMaven(assets);
        }
        Updater.cacheMods(assets);
        val packMods = Updater.gatherExistingMods();
        Updater.updateModpackMods(assets, packMods);
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
}
