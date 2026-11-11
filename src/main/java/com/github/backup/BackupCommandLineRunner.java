package com.github.backup;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

@Component
@ConditionalOnProperty(name = "backup.mode", havingValue = "cli", matchIfMissing = true)
public class BackupCommandLineRunner implements CommandLineRunner, ExitCodeGenerator {

    static final int EXIT_BACKUP_FAILED = 1;
    static final int EXIT_USAGE_ERROR = 2;

    private final BackupService backupService;
    private final UsageReportingService usageReporting;
    private int exitCode = 0;

    public BackupCommandLineRunner(BackupService backupService, UsageReportingService usageReporting) {
        this.backupService = backupService;
        this.usageReporting = usageReporting;
    }

    @Override
    public void run(String... args) throws Exception {
        // Check if interactive mode is requested
        if (args.length > 0 && ("-i".equals(args[0]) || "--interactive".equals(args[0]))) {
            runInteractiveMode();
            return;
        }

        List<String> targets = new ArrayList<>();
        for (String arg : args) {
            String value = arg.trim();
            if ("-h".equals(value) || "--help".equals(value)) {
                printUsage(System.out);
                return;
            }
            if (isSpringProperty(value)) {
                continue;
            }
            if (value.startsWith("-")) {
                // A GitHub name cannot start with a hyphen, so this can only be a mistyped option
                System.err.println("Error: Unknown option: " + value);
                printUsage(System.err);
                exitCode = EXIT_USAGE_ERROR;
                return;
            }
            targets.add(value);
        }

        if (targets.isEmpty()) {
            printUsage(System.out);
            return;
        }

        System.out.println("GitHub Backup Tool");
        System.out.println("==================");
        System.out.println("Backing up repositories for: " + targets);
        System.out.println();

        for (String userOrOrg : targets) {
            try {
                backupService.backupUserRepositories(userOrOrg);
            } catch (Exception e) {
                System.err.println("Error backing up " + userOrOrg + ": " + e.getMessage());
                exitCode = EXIT_BACKUP_FAILED;
            }
        }

        System.out.println(exitCode == 0 ? "\nBackup completed!" : "\nBackup completed with errors.");
        usageReporting.backupCompleted();
    }

    /**
     * The process exit status: non-zero when a non-interactive run failed to back up a
     * user or organization, or was given an unknown option.
     */
    @Override
    public int getExitCode() {
        return exitCode;
    }

    /** A {@code --name=value} argument sets a Spring property, which has already been applied by the time this runs. */
    private static boolean isSpringProperty(String arg) {
        return arg.startsWith("--") && arg.contains("=");
    }

    private static void printUsage(PrintStream out) {
        out.println("Usage: java -jar gh-backup.jar [options] <user/org1> [user/org2] ...");
        out.println("\nOptions:");
        out.println("  -i, --interactive    Start interactive mode");
        out.println("  -h, --help           Show this usage message");
        out.println("\nExample: java -jar gh-backup.jar octocat github");
        out.println("         java -jar gh-backup.jar -i");
        out.println("\nOptional: Set GITHUB_TOKEN environment variable for authenticated access");
    }

    private void runInteractiveMode() {
        System.out.println("GitHub Backup Tool - Interactive Mode");
        System.out.println("=====================================");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  backup <user/org>  - Backup repositories for a user or organization");
        System.out.println("  status             - View current backup status");
        System.out.println("  help               - Show available commands");
        System.out.println("  exit               - Exit interactive mode");
        System.out.println();
        
        @SuppressWarnings("resource") // Scanner wraps System.in, which should not be closed
        Scanner scanner = new Scanner(System.in);
        boolean running = true;

        while (running) {
            System.out.print("> ");

            if (!scanner.hasNextLine()) {
                // End of input (Ctrl-D, Ctrl-Z, or a piped file without exit) ends the session like exit
                System.out.println();
                System.out.println("Exiting interactive mode...");
                break;
            }

            String input = scanner.nextLine().trim();

            if (input.isEmpty()) {
                continue;
            }

            String[] parts = input.split("\\s+", 2);
            String command = parts[0].toLowerCase();

            switch (command) {
                case "backup":
                    if (parts.length < 2) {
                        System.out.println("Error: Please specify a user or organization");
                        System.out.println("Usage: backup <user/org> [user/org2] ...");
                    } else {
                        // Several names are backed up one after another, as on the command line
                        for (String userOrOrg : parts[1].trim().split("\\s+")) {
                            try {
                                backupService.backupUserRepositories(userOrOrg);
                            } catch (Exception e) {
                                System.err.println("Error backing up " + userOrOrg + ": " + e.getMessage());
                            }
                        }
                        usageReporting.backupCompleted();
                    }
                    break;

                case "status":
                    backupService.showBackupStatus();
                    break;

                case "help":
                    System.out.println("\nAvailable Commands:");
                    System.out.println("  backup <user/org>  - Backup repositories for a user or organization");
                    System.out.println("  status             - View current backup status");
                    System.out.println("  help               - Show this help message");
                    System.out.println("  exit               - Exit interactive mode");
                    System.out.println();
                    break;

                case "exit":
                case "quit":
                    System.out.println("Exiting interactive mode...");
                    running = false;
                    break;

                default:
                    System.out.println("Unknown command: " + command);
                    System.out.println("Type 'help' for available commands or 'exit' to quit.");
                    break;
            }
        }

        // Note: We intentionally don't close the scanner since it wraps System.in
        // Closing it would close System.in for the entire JVM process
    }
}
