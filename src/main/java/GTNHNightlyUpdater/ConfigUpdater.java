package GTNHNightlyUpdater;

import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.Scanner;

@Log4j2(topic = "GTNHNightlyUpdater")
public class ConfigUpdater {
    static final String CONFIG_REPO = "https://github.com/GTNewHorizons/GT-New-Horizons-Modpack";
    private final File minecraftDir;
    private final String configTag;


    ConfigUpdater(File minecraftDir, String configTag) {
        this.minecraftDir = minecraftDir;
        this.configTag = configTag;
    }

    public void run() throws IOException {
        File packConfigsDir = new File(minecraftDir, ".updater_pack_configs");

        // Check if Git is installed
        if (!isGitInstalled()) {
            log.warn("Git is not installed on your system, not handling configs");
            return;
        }

        log.info("Updating configs from tag/branch '{}'", configTag != null ? configTag : "origin/master");

        // Init repo if needed
        if (initializeGitRepository(packConfigsDir)) {
            System.out.printf("WARNING: In order to have proper tracking of configs, your configs will be replaced with the latest from [%s [%s]]%n", CONFIG_REPO, configTag);
            Scanner scanner = new Scanner(System.in);
            while (true) {
                System.out.println("Press 'y' to confirm");
                // dev note: there is a bug in intellij where the console returns without user input
                String input = scanner.nextLine();
                // String input = "y";

                if (input.equalsIgnoreCase("y")) {
                    break;
                } else {
                    System.out.println("Invalid input. Please try again.");
                }
            }
        } else {
            // Copy current player's configs to .pack_configs/config
            copyConfigurations(minecraftDir, packConfigsDir);

            // Stage and commit changes
            stageAndCommitChanges(packConfigsDir);

            // Merge any changes
            mergeChanges(packConfigsDir);
        }

        // Copy .pack_configs/config to .minecraft/config
        copyConfigurations(packConfigsDir, minecraftDir);
    }


    private boolean isGitInstalled() {
        try {
            runCommand("git", "--version");
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private boolean initializeGitRepository(File configDirectory) throws IOException {
        if (!new File(configDirectory, ".git").exists()) {
            runCommand("git", "clone", "--depth", "1", "--branch", configTag != null ? configTag : "master", CONFIG_REPO, configDirectory.getAbsolutePath());
            runCommand("git", "-C", configDirectory.getAbsolutePath(), "checkout", "-b", "local");
            File dest = new File(minecraftDir, "config_backup_updater");
            deleteExistingFiles(dest);
            FileUtils.copyDirectory(new File(minecraftDir, "config"), dest);
            log.info("Backed up original config to {}", dest);
            return true;
        }

        return false;
    }

    private void deleteExistingFiles(File directory) {
        log.debug("Deleting [{}]", directory);
        if (directory.exists()) {
            for (File file : directory.listFiles()) {
                if (file.isDirectory()) {
                    deleteExistingFiles(file);
                    file.delete();
                } else {
                    file.delete();
                }
            }
            directory.mkdirs(); // Re-create the directory
        } else {
            directory.mkdirs();
        }
    }

    private void copyConfigurations(File sourceDir, File targetDir) throws IOException {
        File source = new File(sourceDir, "config");
        File dest = new File(targetDir, "config");
        log.debug("Copying from [{}] to [{}]", source, dest);

        deleteExistingFiles(dest);

        FileUtils.copyDirectory(source, dest);
    }

    private void stageAndCommitChanges(File packConfigsDir) throws IOException {
        log.info("Staging and commiting config changes");
        runCommand("git", "-C", packConfigsDir.getAbsolutePath(), "add", ".");
        runCommand("git", "-C", packConfigsDir.getAbsolutePath(), "commit", "--allow-empty", "-m", "Update configurations from player's config directory");
    }

    private void mergeChanges(File packConfigsDir) throws IOException {
        log.info("Merging changes");
        if (configTag != null) {
            runCommand("git", "-C", packConfigsDir.getAbsolutePath(), "fetch", "--no-tags", "origin", "tag", configTag);
        } else {
            runCommand("git", "-C", packConfigsDir.getAbsolutePath(), "fetch", "--no-tags", "origin", "master");
        }
        try {
            // Merge remote changes into local branch
            runCommand("git", "-C", packConfigsDir.getAbsolutePath(), "merge", "--no-stat", "--no-edit", "-X", "theirs", configTag != null ? configTag : "origin/master");
        } catch (RuntimeException e) {
            throw new RuntimeException(String.format("There are conflicts that need to be resolved manually. Please check the repo at [%s]", packConfigsDir.getAbsolutePath()));
        }
    }

    private static void runCommand(String... command) throws IOException {
        log.info("Executing '{}'", String.join(" ", command));
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.inheritIO();
        Process process = processBuilder.start();
        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("Git command failed with exit code " + exitCode);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for Git command to complete", e);
        }
    }
}
