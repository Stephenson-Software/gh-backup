package com.github.backup;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.context.TestPropertySource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

@SpringBootTest
@TestPropertySource(properties = "usage.reporting.enabled=false") // never report to the trace service from a test
class GhBackupApplicationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(BackupService.class, () -> mock(BackupService.class))
            .withBean(UsageReportingService.class, () -> mock(UsageReportingService.class))
            .withUserConfiguration(BackupCommandLineRunner.class);

    @Test
    void contextLoads() {
        // This test ensures the Spring application context loads successfully
    }

    @Test
    void testIsCommandLineRun_DefaultMode() {
        contextRunner.run(context -> assertTrue(GhBackupApplication.isCommandLineRun(context)));
    }

    @Test
    void testIsCommandLineRun_WebMode() {
        contextRunner.withPropertyValues("backup.mode=web")
                .run(context -> assertFalse(GhBackupApplication.isCommandLineRun(context)));
    }

    @Test
    void testIsCommandLineRun_DaemonMode() {
        contextRunner.withPropertyValues("backup.mode=daemon")
                .run(context -> assertFalse(GhBackupApplication.isCommandLineRun(context)));
    }

    @Test
    void testExitStatus_FailedBackupReachesSpringApplicationExit() {
        contextRunner.run(context -> {
            doThrow(new IOException("Test exception"))
                    .when(context.getBean(BackupService.class)).backupUserRepositories("erroruser");

            runQuietly(context.getBean(BackupCommandLineRunner.class), "erroruser");

            assertEquals(BackupCommandLineRunner.EXIT_BACKUP_FAILED, SpringApplication.exit(context));
        });
    }

    @Test
    void testExitStatus_SuccessfulBackupReachesSpringApplicationExit() {
        contextRunner.run(context -> {
            runQuietly(context.getBean(BackupCommandLineRunner.class), "gooduser");

            assertEquals(0, SpringApplication.exit(context));
        });
    }

    @Test
    void testExitStatus_UnknownOptionReachesSpringApplicationExit() {
        contextRunner.run(context -> {
            runQuietly(context.getBean(BackupCommandLineRunner.class), "--interactiv");

            assertEquals(BackupCommandLineRunner.EXIT_USAGE_ERROR, SpringApplication.exit(context));
        });
    }

    private static void runQuietly(BackupCommandLineRunner runner, String... args) throws Exception {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        PrintStream discard = new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8);
        System.setOut(discard);
        System.setErr(discard);
        try {
            runner.run(args);
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }
}
