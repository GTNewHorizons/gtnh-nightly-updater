package GTNHNightlyUpdater;

import lombok.Getter;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Log4j2(topic = "GTNHNightlyUpdater-Main")
public class Main {
    public static void main(String[] args) {
        val options = new Options();
        try {
            new CommandLine(options)
                    .setCaseInsensitiveEnumValuesAllowed(true)
                    .parseArgs(args);

            val updater = new Updater(options.useLatest);
            val cacheDir = getCacheDir().resolve("gtnh-nightly-updater");
            if (Files.notExists(cacheDir)) {
                Files.createDirectory(cacheDir);
            }
            val modExclusions = getModExclusions(cacheDir);

            val assets = updater.fetchDAXXLAssets();

            if (options.configsOnly) {
                for (val instance : options.instances) {
                    log.info("Updating configs for {} with side {}", instance.config.minecraftDir, instance.config.side);
                    new ConfigUpdater(instance.config.minecraftDir.toFile(), assets.getConfigTag()).run();
                }
                return;
            }

            val localAssets = cacheDir.resolve("local-assets.txt");

            val modCacheDir = cacheDir.resolve("mods");
            if (Files.notExists(modCacheDir)) {
                Files.createDirectory(modCacheDir);
            }

            if (options.useLatest) {
                if (Files.exists(localAssets)) {
                    updater.addLocalAssets(assets, localAssets);
                }
                updater.updateModsFromMaven(assets);
            }
            updater.cacheMods(assets, modCacheDir);
            for (val instance : options.instances) {
                log.info("Updating {} with side {}", instance.config.minecraftDir, instance.config.side);
                updater.updateModpackMods(assets, modCacheDir, modExclusions, instance.config);
                new ConfigUpdater(instance.config.minecraftDir.toFile(), assets.getConfigTag()).run();
            }
        } catch (CommandLine.ParameterException e) {
            log.fatal("Parsing fatal: {}", e.getMessage());
            CommandLine.usage(options, System.out);
            System.exit(2);
        } catch (Exception e) {
            log.fatal("Fataled", e);
            System.out.println("Closing in 10 seconds");
            try {
                Thread.sleep(10000);
            } catch (InterruptedException ignored) {
            }
            System.exit(1);
        }
    }


    private static Path getCacheDir() {
        val osName = System.getProperty("os.name").toLowerCase();
        Path cacheDir;
        if (osName.contains("win")) {
            cacheDir = Path.of(System.getenv("LOCALAPPDATA"));
        } else if (osName.contains("mac")) {
            cacheDir = Path.of(System.getenv("HOME"), "Library", "Caches");
        } else {
            String cache = System.getenv("XDG_CACHE_HOME");
            if (cache != null) {
                cacheDir = Path.of(cache);
            } else {
                cacheDir = Path.of(System.getenv("HOME"), ".cache");
            }
        }

        if (Files.notExists(cacheDir)) {
            throw new RuntimeException(String.format("Cache directory not found: `%s`", cacheDir));
        }
        return cacheDir;
    }

    private static Set<String> getModExclusions(Path cacheDir) throws IOException {
        if (Files.exists(cacheDir.resolve("mod-exclusions.txt"))) {
            return new HashSet<>(Files.readAllLines(cacheDir.resolve("mod-exclusions.txt")));
        }
        return new HashSet<>();
    }


    @ToString
    public static class Options {
        @CommandLine.Option(names = {"-l", "--latest"}, description = "Use the latest version of GTNH org mods instead of the latest nightly.")
        private boolean useLatest = false;

        @CommandLine.Option(names = {"-C", "--configs-only"}, description = "Only update configs")
        private boolean configsOnly = false;

        @CommandLine.Option(names = {"-c", "--configs"}, description = "Update configs in addition to mods")
        private boolean updateConfigs = false;

        @CommandLine.ArgGroup(exclusive = false, multiplicity = "1..*")
        List<Instance> instances;

        static class Instance {
            @CommandLine.Option(names = "--add", required = true, description = "Used to add instances to be updated; Allows for updating a client and server at the same time.")
            boolean add_instance; // leave this for the option

            @CommandLine.ArgGroup(exclusive = false, multiplicity = "1")
            InstanceConfig config;

            static class InstanceConfig {
                @Getter
                private Path minecraftDir;
                @CommandLine.Spec
                CommandLine.Model.CommandSpec spec;

                @CommandLine.Option(names = {"-m", "--minecraft"}, required = true, description = "Path to the base minecraft folder to be updated (it contains the mods and config folder).")
                void setMinecraftDir(String value) {
                    val path = Path.of(value);
                    if (!Files.exists(path)) {
                        throw new CommandLine.ParameterException(spec.commandLine(), String.format("Invalid value '%s' for option '--minecraft': path does not exist", path));
                    }
                    this.minecraftDir = path;
                }

                @CommandLine.Option(names = {"-s", "--side"}, required = true, description = "Denotes the mods that should be used; Valid values: ${COMPLETION-CANDIDATES}")
                @Getter
                Side side;

                enum Side {
                    CLIENT,
                    SERVER
                }

                @CommandLine.Option(names = {"-S", "--symlinks"}, description = "Use symlinks instead of copying files to the mods directory; Mac/Linux only. Must be on the same filesystem.")
                @Getter
                private boolean useSymlinks = false;
            }
        }
    }
}
