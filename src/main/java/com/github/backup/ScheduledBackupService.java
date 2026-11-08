package com.github.backup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

/**
 * Service that runs scheduled backups at configured intervals.
 * Enabled when backup.mode=daemon.
 */
@Service
@ConditionalOnProperty(name = "backup.mode", havingValue = "daemon")
public class ScheduledBackupService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledBackupService.class);
    private static final int SEPARATOR_LENGTH = 80;

    private final BackupService backupService;
    private final UsageReportingService usageReporting;
    private final List<String> scheduledUsers;
    private final long backupIntervalMs;
    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public ScheduledBackupService(BackupService backupService,
                                   UsageReportingService usageReporting,
                                   @Value("${backup.scheduled.users:}") String scheduledUsersConfig,
                                   @Value("${backup.scheduled.interval.ms:86400000}") long backupIntervalMs) {
        this.backupService = backupService;
        this.usageReporting = usageReporting;
        this.backupIntervalMs = backupIntervalMs;
        // Parse comma-separated list of users/orgs
        this.scheduledUsers = scheduledUsersConfig.isBlank() 
            ? List.of() 
            : Arrays.stream(scheduledUsersConfig.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    @PostConstruct
    public void init() {
        if (scheduledUsers.isEmpty()) {
            log.warn("No users/organizations configured for scheduled backups.");
            log.warn("Set the 'backup.scheduled.users' property with a comma-separated list.");
            log.warn("Example: backup.scheduled.users=octocat,github");
        } else {
            log.info("GitHub Backup Daemon Mode");
            log.info("========================");
            log.info("Scheduled backups enabled for: {}", String.join(", ", scheduledUsers));
            log.info("Backup interval: {} hours", backupIntervalMs / 3600000.0);
            log.info("First backup will run immediately, then every {} hours.", backupIntervalMs / 3600000.0);
        }
    }

    /**
     * Runs backup for all configured users/organizations.
     * Executes on startup and then at the configured interval after each completion.
     * Uses fixedDelay to ensure the next backup starts only after the previous one completes.
     */
    @Scheduled(fixedDelayString = "${backup.scheduled.interval.ms:86400000}", initialDelay = 0)
    public void runScheduledBackup() {
        if (scheduledUsers.isEmpty()) {
            return;
        }

        String timestamp = LocalDateTime.now().format(dateTimeFormatter);
        log.info("");
        log.info("{}", "=".repeat(SEPARATOR_LENGTH));
        log.info("Starting scheduled backup at {}", timestamp);
        log.info("{}", "=".repeat(SEPARATOR_LENGTH));

        for (String userOrOrg : scheduledUsers) {
            try {
                backupService.backupUserRepositories(userOrOrg);
            } catch (Exception e) {
                // Any failure, checked or not, is this user's alone; the others still run
                log.error("Error backing up {}: {}", userOrOrg, e.getMessage());
            }
        }

        timestamp = LocalDateTime.now().format(dateTimeFormatter);
        log.info("");
        log.info("{}", "=".repeat(SEPARATOR_LENGTH));
        log.info("Scheduled backup completed at {}", timestamp);
        log.info("Next backup will run {} hours after this backup completes.", backupIntervalMs / 3600000.0);
        log.info("{}", "=".repeat(SEPARATOR_LENGTH));
        log.info("");
        usageReporting.backupCompleted();
    }
}
