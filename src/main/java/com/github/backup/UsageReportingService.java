package com.github.backup;

import com.github.backup.trace.TraceClient;
import com.github.backup.trace.TraceInstallId;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Reports that gh-backup was used, to the trace service, and never gets in the
 * way of a backup.
 *
 * <p>Two events are sent, both off the calling thread through the vendored
 * {@link TraceClient}: {@code startup} once per process and
 * {@code backup-completed} when a backup run finishes, each tagged with the
 * program version only. Nothing identifying is sent: no user or
 * organization names, no repository names, no counts, no paths, no hostnames.
 *
 * <p>Reporting is on by default and switched off with
 * {@code usage.reporting.enabled=false} (as a {@code -D} system property, in
 * {@code application.properties}, or as {@code USAGE_REPORTING_ENABLED=false}
 * in the environment), or with the environment variables every trace client
 * honours, {@code TRACE_USAGE_REPORTING=off} and {@code DO_NOT_TRACK=1}, which
 * the client checks before anything this service passes it. The first time
 * reporting runs on a machine, one notice is logged saying so; a marker file
 * under the user's config directory ({@code ~/.config/gh-backup/}) keeps it
 * from being repeated. gh-backup has no settings file of its own, which is why
 * a marker file is used. Details:
 * https://danielstephenson.dev/usage-reporting
 *
 * <p>Every path through this class is exception-safe: a bad endpoint, an
 * unwritable home directory or an unreachable trace server leave the backup
 * untouched.
 */
@Service
public class UsageReportingService {

    private static final Logger log = LoggerFactory.getLogger(UsageReportingService.class);

    /** The name the program key was issued for. */
    static final String APPLICATION = "gh-backup";
    static final String STARTUP_EVENT = "startup";
    static final String BACKUP_COMPLETED_EVENT = "backup-completed";
    static final String NOTICE_MARKER_FILE = "usage-reporting-notice-shown";
    /** The public page describing what trace collects and every way to turn it off. */
    static final String DETAILS_URL = "https://danielstephenson.dev/usage-reporting";
    /** Sent as the version when the build did not supply one. */
    static final String UNKNOWN_VERSION = "unknown";

    private final TraceClient client;
    private final Path noticeMarker;

    @Autowired
    public UsageReportingService(
            @Value("${usage.reporting.enabled:true}") String enabled,
            @Value("${usage.reporting.endpoint:https://trace.danielstephenson.dev}") String endpoint,
            @Value("${usage.reporting.key:}") String key,
            @Value("${spring.application.version:}") String version) {
        this(enabled, endpoint, key, version, defaultNoticeMarker());
    }

    UsageReportingService(String enabled, String endpoint, String key, String version, Path noticeMarker) {
        this.client = buildClient(enabled, endpoint, key, versionOrUnknown(version), installIdFile(noticeMarker));
        this.noticeMarker = noticeMarker;
    }

    /**
     * The program version sent with every event. An unfiltered "@project.version@" means the
     * build did not run through Maven; that, or a blank value, is sent as "unknown".
     */
    static String versionOrUnknown(String version) {
        String trimmed = version == null ? "" : version.trim();
        return trimmed.isEmpty() || trimmed.startsWith("@") ? UNKNOWN_VERSION : trimmed;
    }

    /**
     * The installation ID file, {@value TraceInstallId#FILE_NAME}, next to the notice marker in
     * gh-backup's own directory ({@code ~/.config/gh-backup/}); null if there is no usable home.
     */
    static File installIdFile(Path noticeMarker) {
        return noticeMarker == null ? null : noticeMarker.resolveSibling(TraceInstallId.FILE_NAME).toFile();
    }

    private static TraceClient buildClient(String enabled, String endpoint, String key, String version,
                                           File installIdFile) {
        // A blank value (an empty environment variable, say) means "default", i.e. on.
        boolean on = enabled == null || enabled.isBlank() || !"false".equalsIgnoreCase(enabled.trim());
        try {
            return TraceClient.builder(endpoint, APPLICATION, version)
                    .key(key)
                    .enabled(on)
                    .installId(TraceInstallId.fromEnvironment())
                    .installIdFile(installIdFile)
                    .logger(java.util.logging.Logger.getLogger(UsageReportingService.class.getName()))
                    .build();
        } catch (RuntimeException badConfiguration) {
            log.debug("Usage reporting disabled: {}", badConfiguration.getMessage());
            return TraceClient.disabled();
        }
    }

    /** {@code ~/.config/gh-backup/usage-reporting-notice-shown}; null if there is no usable home. */
    static Path defaultNoticeMarker() {
        String home = System.getProperty("user.home");
        if (home == null || home.isBlank()) {
            return null;
        }
        return Paths.get(home, ".config", APPLICATION, NOTICE_MARKER_FILE);
    }

    /** Whether events are actually sent. */
    public boolean isEnabled() {
        return client.isEnabled();
    }

    /** The random installation ID sent as the tag {@code install}, or null while reporting is off. */
    public String installId() {
        return client.installId();
    }

    @PostConstruct
    void start() {
        if (!client.isEnabled()) {
            return;
        }
        showFirstRunNoticeOnce();
        client.report(STARTUP_EVENT);
    }

    /** Reports that a backup run finished. Carries the version only, nothing about what was backed up. */
    public void backupCompleted() {
        client.report(BACKUP_COMPLETED_EVENT);
    }

    /**
     * Stops the sending thread, waiting briefly (at most the client's read timeout)
     * for a report in flight, so a short CLI run does not exit before its startup
     * event has left the machine. Bound to context shutdown, which Spring Boot's
     * shutdown hook runs when the process ends.
     */
    @PreDestroy
    public void close() {
        client.close();
    }

    private void showFirstRunNoticeOnce() {
        if (noticeMarker == null) {
            return;
        }
        try {
            if (Files.exists(noticeMarker)) {
                return;
            }
            log.info("Usage reporting is on: gh-backup sends its name, version and a random installation ID "
                    + "with a startup event and a backup-completed event (nothing else) to "
                    + "https://trace.danielstephenson.dev - nothing about accounts or repositories. Turn it off with "
                    + "-Dusage.reporting.enabled=false, USAGE_REPORTING_ENABLED=false or "
                    + "TRACE_USAGE_REPORTING=off. Details: " + DETAILS_URL);
            Files.createDirectories(noticeMarker.getParent());
            Files.writeString(noticeMarker, "The usage-reporting notice was shown once; delete this file to see it again.\n");
        } catch (IOException | RuntimeException cannotPersist) {
            // The notice is shown again next run; that is the worst case, and it is harmless.
            log.debug("Could not record that the usage-reporting notice was shown: {}", cannotPersist.getMessage());
        }
    }
}
