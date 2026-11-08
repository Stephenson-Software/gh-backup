package com.github.backup;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ScheduledBackupServiceTest {

    private static final long DEFAULT_INTERVAL = 86400000L; // 24 hours
    private static final long ONE_HOUR_INTERVAL = 3600000L; // 1 hour

    @Mock
    private BackupService backupService;

    @Mock
    private UsageReportingService usageReporting;

    private ScheduledBackupService scheduledBackupService;
    private ListAppender<ILoggingEvent> logAppender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Set up log appender to capture log messages
        logger = (Logger) LoggerFactory.getLogger(ScheduledBackupService.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        if (logAppender != null) {
            logger.detachAppender(logAppender);
        }
    }

    @Test
    void testScheduledBackupService_WithNoUsers() {
        scheduledBackupService = new ScheduledBackupService(backupService, usageReporting, "", DEFAULT_INTERVAL);
        
        // Should not throw exception with empty users list
        assertDoesNotThrow(() -> scheduledBackupService.runScheduledBackup());
        
        // Should not call backup service when no users configured
        verifyNoInteractions(backupService);
        // ...and a run that backed up nothing is not reported as a completed backup
        verifyNoInteractions(usageReporting);
    }

    @Test
    void testScheduledBackupService_WithSingleUser() throws IOException {
        scheduledBackupService = new ScheduledBackupService(backupService, usageReporting, "octocat", DEFAULT_INTERVAL);
        
        scheduledBackupService.runScheduledBackup();
        
        // Should call backup service once for the configured user
        verify(backupService, times(1)).backupUserRepositories("octocat");
    }

    @Test
    void testScheduledBackupService_WithMultipleUsers() throws IOException {
        scheduledBackupService = new ScheduledBackupService(backupService, usageReporting, "octocat,github,spring-projects", DEFAULT_INTERVAL);
        
        scheduledBackupService.runScheduledBackup();
        
        // Should call backup service for each configured user
        verify(backupService, times(1)).backupUserRepositories("octocat");
        verify(backupService, times(1)).backupUserRepositories("github");
        verify(backupService, times(1)).backupUserRepositories("spring-projects");

        // One scheduled run, one backup-completed report, however many users it covered
        verify(usageReporting, times(1)).backupCompleted();
    }

    @Test
    void testScheduledBackupService_WithWhitespace() throws IOException {
        scheduledBackupService = new ScheduledBackupService(backupService, usageReporting, " octocat , github , spring-projects ", DEFAULT_INTERVAL);
        
        scheduledBackupService.runScheduledBackup();
        
        // Should trim whitespace from user names
        verify(backupService, times(1)).backupUserRepositories("octocat");
        verify(backupService, times(1)).backupUserRepositories("github");
        verify(backupService, times(1)).backupUserRepositories("spring-projects");
    }

    @Test
    void testScheduledBackupService_HandlesException() throws IOException {
        scheduledBackupService = new ScheduledBackupService(backupService, usageReporting, "octocat,github", DEFAULT_INTERVAL);
        
        doThrow(new IOException("Test exception")).when(backupService).backupUserRepositories("octocat");
        
        // Should continue to next user even if one fails
        assertDoesNotThrow(() -> scheduledBackupService.runScheduledBackup());
        
        verify(backupService, times(1)).backupUserRepositories("octocat");
        verify(backupService, times(1)).backupUserRepositories("github");
    }

    @Test
    void testScheduledBackupService_WithEmptyStrings() throws IOException {
        scheduledBackupService = new ScheduledBackupService(backupService, usageReporting, "octocat,,github", DEFAULT_INTERVAL);
        
        scheduledBackupService.runScheduledBackup();
        
        // Should filter out empty strings and only call for valid users
        verify(backupService, times(1)).backupUserRepositories("octocat");
        verify(backupService, times(1)).backupUserRepositories("github");
        verifyNoMoreInteractions(backupService);
    }

    @Test
    void testScheduledBackupService_WithBlankString() {
        scheduledBackupService = new ScheduledBackupService(backupService, usageReporting, "   ", DEFAULT_INTERVAL);
        
        assertDoesNotThrow(() -> scheduledBackupService.runScheduledBackup());
        
        // Should not call backup service with blank configuration
        verifyNoInteractions(backupService);
    }

    @Test
    void testScheduledBackupService_WithCustomInterval() {
        scheduledBackupService = new ScheduledBackupService(backupService, usageReporting, "octocat", ONE_HOUR_INTERVAL);
        
        // Should not throw exception with custom interval
        assertDoesNotThrow(() -> scheduledBackupService.runScheduledBackup());
    }

    @Test
    void testInit_WithNoUsers_LogsWarning() {
        scheduledBackupService = new ScheduledBackupService(backupService, usageReporting, "", DEFAULT_INTERVAL);
        scheduledBackupService.init();
        
        // Verify warning logs are present
        List<ILoggingEvent> logsList = logAppender.list;
        assertTrue(logsList.stream().anyMatch(event -> 
            event.getLevel() == Level.WARN && 
            event.getFormattedMessage().contains("No users/organizations configured")),
            "Should log warning when no users configured");
    }

    @Test
    void testInit_WithUsers_LogsInfo() {
        scheduledBackupService = new ScheduledBackupService(backupService, usageReporting, "octocat,github", DEFAULT_INTERVAL);
        scheduledBackupService.init();
        
        // Verify info logs are present
        List<ILoggingEvent> logsList = logAppender.list;
        assertTrue(logsList.stream().anyMatch(event -> 
            event.getLevel() == Level.INFO && 
            event.getFormattedMessage().contains("GitHub Backup Daemon Mode")),
            "Should log info header when users are configured");
        
        assertTrue(logsList.stream().anyMatch(event -> 
            event.getLevel() == Level.INFO && 
            event.getFormattedMessage().contains("Scheduled backups enabled for: octocat, github")),
            "Should log configured users");
    }

    @Test
    void testInit_DisplaysCorrectIntervalInHours() {
        scheduledBackupService = new ScheduledBackupService(backupService, usageReporting, "octocat", ONE_HOUR_INTERVAL);
        scheduledBackupService.init();
        
        // Verify interval is displayed correctly
        List<ILoggingEvent> logsList = logAppender.list;
        assertTrue(logsList.stream().anyMatch(event -> 
            event.getFormattedMessage().contains("1.0 hours")),
            "Should display interval as 1.0 hours for 3600000ms");
    }

    @Test
    void testInit_DisplaysDefault24HourInterval() {
        scheduledBackupService = new ScheduledBackupService(backupService, usageReporting, "octocat", DEFAULT_INTERVAL);
        scheduledBackupService.init();
        
        // Verify 24 hour interval is displayed
        List<ILoggingEvent> logsList = logAppender.list;
        assertTrue(logsList.stream().anyMatch(event -> 
            event.getFormattedMessage().contains("24.0 hours")),
            "Should display interval as 24.0 hours for default interval");
    }

    @Test
    void testRunScheduledBackup_LogsStartAndComplete() throws IOException {
        scheduledBackupService = new ScheduledBackupService(backupService, usageReporting, "octocat", DEFAULT_INTERVAL);
        scheduledBackupService.runScheduledBackup();
        
        // Verify backup start and completion are logged
        List<ILoggingEvent> logsList = logAppender.list;
        assertTrue(logsList.stream().anyMatch(event -> 
            event.getFormattedMessage().contains("Starting scheduled backup")),
            "Should log when backup starts");
        
        assertTrue(logsList.stream().anyMatch(event -> 
            event.getFormattedMessage().contains("Scheduled backup completed")),
            "Should log when backup completes");
    }

    @Test
    void testRunScheduledBackup_LogsErrorForFailedUser() throws IOException {
        scheduledBackupService = new ScheduledBackupService(backupService, usageReporting, "octocat,github", DEFAULT_INTERVAL);
        
        doThrow(new IOException("Network error")).when(backupService).backupUserRepositories("octocat");
        
        scheduledBackupService.runScheduledBackup();
        
        // Verify error is logged for failed user
        List<ILoggingEvent> logsList = logAppender.list;
        assertTrue(logsList.stream().anyMatch(event -> 
            event.getLevel() == Level.ERROR && 
            event.getFormattedMessage().contains("Error backing up octocat") &&
            event.getFormattedMessage().contains("Network error")),
            "Should log error with user name and error message");
        
        // Verify github backup was still attempted
        verify(backupService, times(1)).backupUserRepositories("github");
    }

    @Test
    void testRunScheduledBackup_UncheckedExceptionDoesNotStopRemainingUsers() throws IOException {
        scheduledBackupService = new ScheduledBackupService(backupService, usageReporting, "octocat,github", DEFAULT_INTERVAL);

        doThrow(new IllegalStateException("Listing interrupted")).when(backupService).backupUserRepositories("octocat");

        assertDoesNotThrow(() -> scheduledBackupService.runScheduledBackup());

        // The failure is logged like any other, and the rest of the run still happens
        List<ILoggingEvent> logsList = logAppender.list;
        assertTrue(logsList.stream().anyMatch(event ->
            event.getLevel() == Level.ERROR &&
            event.getFormattedMessage().contains("Error backing up octocat") &&
            event.getFormattedMessage().contains("Listing interrupted")),
            "Should log error with user name and error message");
        assertTrue(logsList.stream().anyMatch(event ->
            event.getFormattedMessage().contains("Scheduled backup completed")),
            "Should log when backup completes");
        verify(backupService, times(1)).backupUserRepositories("github");
        verify(usageReporting, times(1)).backupCompleted();
    }

    @Test
    void testRunScheduledBackup_WithNoUsers_NoLogsGenerated() {
        scheduledBackupService = new ScheduledBackupService(backupService, usageReporting, "", DEFAULT_INTERVAL);
        
        logAppender.list.clear(); // Clear any init logs
        scheduledBackupService.runScheduledBackup();
        
        // Should not generate any logs when no users configured
        assertTrue(logAppender.list.isEmpty() || logAppender.list.stream().noneMatch(event -> 
            event.getFormattedMessage().contains("Starting scheduled backup")),
            "Should not log backup start/complete when no users configured");
        
        verifyNoInteractions(backupService);
    }

    @Test
    void testMultipleUserNames_ParsedCorrectly() throws IOException {
        scheduledBackupService = new ScheduledBackupService(
            backupService, 
            usageReporting,
            "user1,user2,user3,user4,user5", 
            DEFAULT_INTERVAL
        );
        
        scheduledBackupService.runScheduledBackup();
        
        // Verify all 5 users are backed up
        verify(backupService, times(1)).backupUserRepositories("user1");
        verify(backupService, times(1)).backupUserRepositories("user2");
        verify(backupService, times(1)).backupUserRepositories("user3");
        verify(backupService, times(1)).backupUserRepositories("user4");
        verify(backupService, times(1)).backupUserRepositories("user5");
        verify(backupService, times(5)).backupUserRepositories(anyString());
    }

    @Test
    void testSpecialCharactersInUserNames() throws IOException {
        // Test that usernames with hyphens and underscores work correctly
        scheduledBackupService = new ScheduledBackupService(
            backupService, 
            usageReporting,
            "user-with-dash,user_with_underscore,user.with.dots", 
            DEFAULT_INTERVAL
        );
        
        scheduledBackupService.runScheduledBackup();
        
        verify(backupService, times(1)).backupUserRepositories("user-with-dash");
        verify(backupService, times(1)).backupUserRepositories("user_with_underscore");
        verify(backupService, times(1)).backupUserRepositories("user.with.dots");
    }

    @Test
    void testIntervalAccuracy_SmallInterval() {
        long twoHours = 7200000L; // 2 hours
        scheduledBackupService = new ScheduledBackupService(backupService, usageReporting, "octocat", twoHours);
        scheduledBackupService.init();
        
        // Verify correct hour calculation for 2 hours
        List<ILoggingEvent> logsList = logAppender.list;
        assertTrue(logsList.stream().anyMatch(event -> 
            event.getFormattedMessage().contains("2.0 hours")),
            "Should display interval as 2.0 hours for 7200000ms");
    }

    @Test
    void testIntervalAccuracy_LargeInterval() {
        long oneWeek = 604800000L; // 7 days = 168 hours
        scheduledBackupService = new ScheduledBackupService(backupService, usageReporting, "octocat", oneWeek);
        scheduledBackupService.init();
        
        // Verify correct hour calculation for 168 hours (1 week)
        List<ILoggingEvent> logsList = logAppender.list;
        assertTrue(logsList.stream().anyMatch(event -> 
            event.getFormattedMessage().contains("168.0 hours")),
            "Should display interval as 168.0 hours for 604800000ms");
    }
}

