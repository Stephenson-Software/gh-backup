package com.github.backup;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BackupCommandLineRunnerTest {

    @Mock
    private BackupService backupService;

    @Mock
    private UsageReportingService usageReporting;

    private BackupCommandLineRunner runner;

    private final InputStream originalIn = System.in;
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        runner = new BackupCommandLineRunner(backupService, usageReporting);
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void tearDown() {
        System.setIn(originalIn);
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    private void provideInput(String input) {
        System.setIn(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
    }

    private String stdout() {
        return out.toString(StandardCharsets.UTF_8);
    }

    private String stderr() {
        return err.toString(StandardCharsets.UTF_8);
    }

    @Test
    void testRun_NoArguments() throws Exception {
        assertDoesNotThrow(() -> runner.run());

        // Printing the usage text is not a backup run
        verifyNoInteractions(usageReporting);
    }

    @Test
    void testRun_WithSingleUser() throws Exception {
        doNothing().when(backupService).backupUserRepositories("testuser");

        runner.run("testuser");

        verify(backupService).backupUserRepositories("testuser");
    }

    @Test
    void testRun_WithMultipleUsers() throws Exception {
        doNothing().when(backupService).backupUserRepositories(anyString());

        runner.run("user1", "user2", "user3");

        verify(backupService).backupUserRepositories("user1");
        verify(backupService).backupUserRepositories("user2");
        verify(backupService).backupUserRepositories("user3");

        // One backup run, one backup-completed report, however many users it covered
        verify(usageReporting, times(1)).backupCompleted();
    }

    @Test
    void testRun_HandlesException() throws Exception {
        doThrow(new IOException("Test exception"))
                .when(backupService).backupUserRepositories("erroruser");

        assertDoesNotThrow(() -> runner.run("erroruser"));
        
        verify(backupService).backupUserRepositories("erroruser");
    }

    @Test
    void testRun_NoArguments_PrintsUsage() throws Exception {
        runner.run();

        assertTrue(stdout().contains("Usage: java -jar gh-backup.jar [options] <user/org1> [user/org2] ..."));
        verifyNoInteractions(backupService);
    }

    @Test
    void testRun_HandlesException_ContinuesWithRemainingUsers() throws Exception {
        doThrow(new IOException("Test exception"))
                .when(backupService).backupUserRepositories("erroruser");

        runner.run("erroruser", "gooduser");

        verify(backupService).backupUserRepositories("gooduser");
        assertTrue(stderr().contains("Error backing up erroruser: Test exception"));
        // A failed user does not stop the run being reported as completed
        verify(usageReporting, times(1)).backupCompleted();
    }

    @Test
    void testRun_TrimsArguments() throws Exception {
        runner.run(" octocat ");

        verify(backupService).backupUserRepositories("octocat");
    }

    @Test
    void testRun_InteractiveFlagNotFirst_TreatedAsUserName() throws Exception {
        // The flag is only recognized as the first argument
        runner.run("octocat", "-i");

        verify(backupService).backupUserRepositories("octocat");
        verify(backupService).backupUserRepositories("-i");
        assertFalse(stdout().contains("Interactive Mode"));
    }

    @Test
    void testRun_InteractiveMode_ShortFlag() throws Exception {
        provideInput("exit\n");

        runner.run("-i");

        assertTrue(stdout().contains("GitHub Backup Tool - Interactive Mode"));
        assertTrue(stdout().contains("Exiting interactive mode..."));
        verifyNoInteractions(backupService, usageReporting);
    }

    @Test
    void testRun_InteractiveMode_LongFlag() throws Exception {
        provideInput("exit\n");

        runner.run("--interactive");

        assertTrue(stdout().contains("GitHub Backup Tool - Interactive Mode"));
        verifyNoInteractions(backupService, usageReporting);
    }

    @Test
    void testRun_InteractiveMode_IgnoresArgumentsAfterFlag() throws Exception {
        provideInput("exit\n");

        runner.run("-i", "octocat");

        verifyNoInteractions(backupService);
    }

    @Test
    void testRun_InteractiveMode_BackupCommand() throws Exception {
        provideInput("backup octocat\nexit\n");

        runner.run("-i");

        verify(backupService).backupUserRepositories("octocat");
        verify(usageReporting, times(1)).backupCompleted();
    }

    @Test
    void testRun_InteractiveMode_EachBackupReportedSeparately() throws Exception {
        provideInput("backup user1\nbackup user2\nexit\n");

        runner.run("-i");

        verify(backupService).backupUserRepositories("user1");
        verify(backupService).backupUserRepositories("user2");
        verify(usageReporting, times(2)).backupCompleted();
    }

    @Test
    void testRun_InteractiveMode_CommandIsCaseInsensitiveButArgumentIsNot() throws Exception {
        provideInput("BACKUP OctoCat\nExit\n");

        runner.run("-i");

        verify(backupService).backupUserRepositories("OctoCat");
    }

    @Test
    void testRun_InteractiveMode_BackupArgumentIsTrimmed() throws Exception {
        provideInput("backup    octocat   \nexit\n");

        runner.run("-i");

        verify(backupService).backupUserRepositories("octocat");
    }

    @Test
    void testRun_InteractiveMode_BackupWithoutTarget() throws Exception {
        provideInput("backup\nexit\n");

        runner.run("-i");

        assertTrue(stdout().contains("Error: Please specify a user or organization"));
        assertTrue(stdout().contains("Usage: backup <user/org>"));
        verifyNoInteractions(backupService, usageReporting);
    }

    @Test
    void testRun_InteractiveMode_BackupFailureStaysInPrompt() throws Exception {
        doThrow(new IOException("Test exception"))
                .when(backupService).backupUserRepositories("erroruser");
        provideInput("backup erroruser\nbackup gooduser\nexit\n");

        runner.run("-i");

        assertTrue(stderr().contains("Error backing up erroruser: Test exception"));
        verify(backupService).backupUserRepositories("gooduser");
        // The failed backup is still reported as a completed run
        verify(usageReporting, times(2)).backupCompleted();
    }

    @Test
    void testRun_InteractiveMode_StatusCommand() throws Exception {
        provideInput("status\nexit\n");

        runner.run("-i");

        verify(backupService).showBackupStatus();
        verifyNoInteractions(usageReporting);
    }

    @Test
    void testRun_InteractiveMode_HelpCommand() throws Exception {
        provideInput("help\nexit\n");

        runner.run("-i");

        assertTrue(stdout().contains("Available Commands:"));
        assertTrue(stdout().contains("  help               - Show this help message"));
        verifyNoInteractions(backupService);
    }

    @Test
    void testRun_InteractiveMode_QuitAlias() throws Exception {
        provideInput("quit\n");

        runner.run("-i");

        assertTrue(stdout().contains("Exiting interactive mode..."));
    }

    @Test
    void testRun_InteractiveMode_UnknownCommand() throws Exception {
        provideInput("frobnicate now\nexit\n");

        runner.run("-i");

        assertTrue(stdout().contains("Unknown command: frobnicate"));
        assertTrue(stdout().contains("Type 'help' for available commands or 'exit' to quit."));
        verifyNoInteractions(backupService);
    }

    @Test
    void testRun_InteractiveMode_BlankLinesIgnored() throws Exception {
        provideInput("\n   \nexit\n");

        runner.run("-i");

        assertFalse(stdout().contains("Unknown command"));
        verifyNoInteractions(backupService);
    }

    @Test
    void testRun_InteractiveMode_EndOfInputWithoutExit() throws Exception {
        // End of input before exit/quit (Ctrl-D, a piped file without exit) ends the session like exit (#36)
        provideInput("status\n");

        assertDoesNotThrow(() -> runner.run("-i"));
        verify(backupService).showBackupStatus();
        assertTrue(stdout().contains("Exiting interactive mode..."));
    }

    @Test
    void testRun_InteractiveMode_EmptyInput() throws Exception {
        provideInput("");

        assertDoesNotThrow(() -> runner.run("-i"));
        assertTrue(stdout().contains("Exiting interactive mode..."));
        verifyNoInteractions(backupService, usageReporting);
    }
}
