package com.github.backup;

import com.github.backup.web.BackupStatusResponse;
import com.github.backup.web.BackupStatusResponse.RepositoryInfo;
import com.github.backup.web.BackupStatusResponse.UserBackupInfo;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.kohsuke.github.GHRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

@Service
public class BackupService {

    private final GitHubService gitHubService;
    private final String backupDirectory;

    public BackupService(GitHubService gitHubService, 
                         @Value("${backup.directory}") String backupDirectory) {
        this.gitHubService = gitHubService;
        // Convert to absolute path to support both Linux and Windows
        Path backupPath = Paths.get(backupDirectory).toAbsolutePath().normalize();
        this.backupDirectory = backupPath.toString();
    }

    public void backupUserRepositories(String userOrOrg) throws IOException {
        System.out.println("\nFetching repositories for: " + userOrOrg);
        
        List<GHRepository> repositories = gitHubService.getPublicRepositories(userOrOrg);
        
        if (repositories.isEmpty()) {
            System.out.println("No public repositories found for " + userOrOrg);
            return;
        }

        System.out.println("Found " + repositories.size() + " public repositories");

        Path userBackupPath = Paths.get(backupDirectory, userOrOrg);
        try {
            Files.createDirectories(userBackupPath);
        } catch (IOException e) {
            throw new IOException("Failed to create backup directory '" + userBackupPath + "'", e);
        }

        List<String> failedRepos = new ArrayList<>();
        for (GHRepository repo : repositories) {
            try {
                backupRepository(repo, userBackupPath);
            } catch (Exception e) {
                System.err.println("Failed to backup " + repo.getName() + ": " + e.getMessage());
                failedRepos.add(repo.getName());
            }
        }

        // Display summary of failed repositories
        if (!failedRepos.isEmpty()) {
            System.out.println("\n⚠ Warning: " + failedRepos.size() + " repository(ies) failed to backup:");
            for (String repoName : failedRepos) {
                System.out.println("  - " + repoName);
            }
        }
    }

    public void showBackupStatus() {
        File backupDir = new File(backupDirectory);
        
        if (!backupDir.exists() || !backupDir.isDirectory()) {
            System.out.println("No backups found. Backup directory does not exist: " + backupDirectory);
            return;
        }

        File[] userDirs = backupDir.listFiles(File::isDirectory);
        
        if (userDirs == null || userDirs.length == 0) {
            System.out.println("No backups found.");
            return;
        }

        System.out.println("\nBackup Status");
        System.out.println("=============");
        System.out.println("Backup directory: " + backupDir.getAbsolutePath());
        System.out.println();

        DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault());
        int totalRepos = 0;

        for (File userDir : userDirs) {
            File[] repos = userDir.listFiles(File::isDirectory);
            if (repos == null) {
                System.err.println("Warning: Unable to read directory: " + userDir.getName());
                continue;
            }

            System.out.println(userDir.getName() + "/ (" + repos.length + " repositories)");
            
            for (File repo : repos) {
                totalRepos++;
                long lastModified = repo.lastModified();
                String lastModifiedStr = dateFormat.format(Instant.ofEpochMilli(lastModified));
                System.out.println("  - " + repo.getName() + " (last updated: " + lastModifiedStr + ")");
            }
            System.out.println();
        }

        System.out.println("Total: " + userDirs.length + " users/organizations, " + totalRepos + " repositories");
    }

    public BackupStatusResponse getBackupStatusData() {
        File backupDir = new File(backupDirectory);
        
        if (!backupDir.exists() || !backupDir.isDirectory()) {
            return new BackupStatusResponse(0, 0, new ArrayList<>());
        }

        File[] userDirs = backupDir.listFiles(File::isDirectory);
        
        if (userDirs == null || userDirs.length == 0) {
            return new BackupStatusResponse(0, 0, new ArrayList<>());
        }

        DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault());
        int totalRepos = 0;
        List<UserBackupInfo> users = new ArrayList<>();

        for (File userDir : userDirs) {
            File[] repos = userDir.listFiles(File::isDirectory);
            if (repos == null) {
                continue;
            }

            List<RepositoryInfo> repositories = new ArrayList<>();
            for (File repo : repos) {
                totalRepos++;
                long lastModified = repo.lastModified();
                String lastModifiedStr = dateFormat.format(Instant.ofEpochMilli(lastModified));
                repositories.add(new RepositoryInfo(repo.getName(), lastModifiedStr));
            }
            
            users.add(new UserBackupInfo(userDir.getName(), repos.length, repositories));
        }

        return new BackupStatusResponse(userDirs.length, totalRepos, users);
    }

    private void backupRepository(GHRepository repo, Path backupPath) throws GitAPIException {
        String repoName = repo.getName();
        File localPath = backupPath.resolve(repoName).toFile();

        System.out.print("Backing up " + repoName + "...");

        if (localPath.exists()) {
            // Repository already exists - update it
            try (Git git = Git.open(localPath)) {
                System.out.println(" (repository exists, updating)");
                git.fetch()
                        .setProgressMonitor(SimpleProgressMonitor.forConsole())
                        .call();
                System.out.println("  ✓ Updated successfully");
            } catch (IOException e) {
                System.err.println("  ✗ Failed to update: " + e.getMessage());
                throw new GitAPIException("Failed to update repository", e) {};
            }
        } else {
            // Clone new repository
            System.out.println();  // New line for progress output
            try (Git git = Git.cloneRepository()
                    .setURI(repo.getHttpTransportUrl())
                    .setDirectory(localPath)
                    .setProgressMonitor(SimpleProgressMonitor.forConsole())
                    .call()) {
                System.out.println("  ✓ Cloned successfully");
            }
        }
    }

    /**
     * Progress monitor that shows clone and fetch progress, one line per task.
     *
     * <p>JGit does not call {@link #endTask()} for progress reported by the remote
     * (the {@code remote: ...} tasks), so a task still in progress is finished off
     * when the next one begins. In overwrite mode each update rewrites the current
     * line; otherwise each update is printed on its own line. A percentage is only
     * printed when it changes.
     */
    static class SimpleProgressMonitor implements ProgressMonitor {

        static final long UPDATE_INTERVAL_MS = 500;

        private final PrintStream out;
        private final boolean overwrite;
        private final LongSupplier clock;

        private String currentTask;
        private int totalWork;
        private int completed;
        private int lastPercentage;
        private boolean printed;
        private long lastUpdateTime;

        SimpleProgressMonitor(PrintStream out, boolean overwrite, LongSupplier clock) {
            this.out = out;
            this.overwrite = overwrite;
            this.clock = clock;
        }

        /**
         * Creates a monitor writing to standard output, with the rendering mode
         * taken from the {@code backup.progress.overwrite} system property.
         */
        static SimpleProgressMonitor forConsole() {
            boolean overwrite = Boolean.parseBoolean(System.getProperty("backup.progress.overwrite", "true"));
            return new SimpleProgressMonitor(System.out, overwrite, System::currentTimeMillis);
        }

        @Override
        public void start(int totalTasks) {
            // Called when the overall operation starts
        }

        @Override
        public void beginTask(String title, int totalWork) {
            finishTask(false);
            this.currentTask = title;
            this.totalWork = totalWork;
            this.completed = 0;
            this.lastUpdateTime = clock.getAsLong();
            this.printed = false;
            if (totalWork > 0) {
                print(0);
            } else if (overwrite) {
                out.print("  " + title + "...");
            } else {
                out.println("  " + title + "...");
            }
        }

        @Override
        public void update(int completed) {
            if (currentTask == null) {
                return;
            }
            this.completed += completed;
            long currentTime = clock.getAsLong();

            // Throttle updates to avoid too much output
            if (currentTime - lastUpdateTime < UPDATE_INTERVAL_MS) {
                return;
            }
            lastUpdateTime = currentTime;

            if (totalWork > 0 && this.completed < totalWork) {
                print(percentage());
            }
        }

        @Override
        public void endTask() {
            finishTask(true);
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public void showDuration(boolean enabled) {
            // Controls whether the monitor should display operation duration
            // Not implemented in this basic progress monitor
        }

        /**
         * Prints the final value of the task in progress, if any, and ends its line.
         * A task ended by JGit is complete; one ended only because the next task
         * began shows the last progress it reported.
         */
        private void finishTask(boolean complete) {
            if (currentTask == null) {
                return;
            }
            if (totalWork > 0) {
                print(complete ? 100 : percentage());
            }
            if (overwrite) {
                out.println();
            }
            currentTask = null;
        }

        private int percentage() {
            return (int) Math.min(100, (completed * 100L) / totalWork);
        }

        private void print(int percentage) {
            String line = "  " + currentTask + ": " + percentage + "%";
            if (printed && percentage == lastPercentage) {
                return;
            }
            if (!overwrite) {
                out.println(line);
            } else if (printed) {
                out.print("\r" + line);
            } else {
                out.print(line);
            }
            lastPercentage = percentage;
            printed = true;
        }
    }
}
