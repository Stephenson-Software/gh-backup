package com.github.backup;

import com.github.backup.web.BackupStatusResponse;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kohsuke.github.GHRepository;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BackupServiceTest {

    @Mock
    private GitHubService gitHubService;

    @Mock
    private GHRepository mockRepo1;

    @Mock
    private GHRepository mockRepo2;

    private BackupService backupService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        backupService = new BackupService(gitHubService, tempDir.toString());
    }

    @Test
    void testBackupUserRepositories_NoRepositories() throws IOException {
        when(gitHubService.getPublicRepositories("testuser"))
                .thenReturn(Collections.emptyList());

        backupService.backupUserRepositories("testuser");

        verify(gitHubService).getPublicRepositories("testuser");
    }

    @Test
    void testBackupUserRepositories_WithRepositories() throws Exception {
        Path source = createSourceRepository("source-repo1");
        when(mockRepo1.getName()).thenReturn("repo1");
        when(mockRepo1.getHttpTransportUrl()).thenReturn(source.toUri().toString());

        List<GHRepository> repos = Collections.singletonList(mockRepo1);
        when(gitHubService.getPublicRepositories("testuser")).thenReturn(repos);

        // Clones from a local source repository so no network access is needed
        assertDoesNotThrow(() -> backupService.backupUserRepositories("testuser"));

        verify(gitHubService).getPublicRepositories("testuser");
        assertTrue(Files.isDirectory(tempDir.resolve("testuser").resolve("repo1").resolve(".git")));
    }

    @Test
    void testBackupUserRepositories_ExistingRepositoryIsFetched() throws Exception {
        Path source = createSourceRepository("source-repo1");
        when(mockRepo1.getName()).thenReturn("repo1");
        when(mockRepo1.getHttpTransportUrl()).thenReturn(source.toUri().toString());
        when(gitHubService.getPublicRepositories("testuser"))
                .thenReturn(Collections.singletonList(mockRepo1));

        backupService.backupUserRepositories("testuser");

        String newCommit;
        String branch;
        try (Git sourceGit = Git.open(source.toFile())) {
            branch = sourceGit.getRepository().getBranch();
            newCommit = commitFile(sourceGit, "second.txt", "second").getName();
        }

        String output = captureStdout(() -> backupService.backupUserRepositories("testuser"));

        assertTrue(output.contains("(repository exists, updating)"), output);
        assertTrue(output.contains("✓ Updated successfully"), output);
        try (Git backupGit = Git.open(tempDir.resolve("testuser").resolve("repo1").toFile())) {
            Ref remoteRef = backupGit.getRepository().findRef("refs/remotes/origin/" + branch);
            assertNotNull(remoteRef);
            assertEquals(newCommit, remoteRef.getObjectId().getName());
        }
    }

    @Test
    void testBackupUserRepositories_FailedRepositoryIsSummarizedAndOthersContinue() throws Exception {
        Path source = createSourceRepository("source-repo1");
        when(mockRepo1.getName()).thenReturn("repo1");
        when(mockRepo1.getHttpTransportUrl()).thenReturn(source.toUri().toString());
        when(mockRepo2.getName()).thenReturn("repo2");
        when(mockRepo2.getHttpTransportUrl())
                .thenReturn(tempDir.resolve("does-not-exist").toUri().toString());
        when(gitHubService.getPublicRepositories("testuser"))
                .thenReturn(Arrays.asList(mockRepo2, mockRepo1));

        String output = captureStdout(() -> backupService.backupUserRepositories("testuser"));

        assertTrue(output.contains("Found 2 public repositories"), output);
        assertTrue(output.contains("⚠ Warning: 1 repository(ies) failed to backup:"), output);
        assertTrue(output.contains("  - repo2"), output);
        assertTrue(Files.isDirectory(tempDir.resolve("testuser").resolve("repo1").resolve(".git")));
    }

    @Test
    void testBackupUserRepositories_BackupDirectoryCannotBeCreated() throws IOException {
        Path blocker = Files.createFile(tempDir.resolve("blocker"));
        BackupService service = new BackupService(gitHubService, blocker.toString());
        when(gitHubService.getPublicRepositories("testuser"))
                .thenReturn(Collections.singletonList(mockRepo1));

        IOException e = assertThrows(IOException.class, () -> service.backupUserRepositories("testuser"));

        assertTrue(e.getMessage().startsWith("Failed to create backup directory '"), e.getMessage());
        assertTrue(e.getMessage().contains(blocker.resolve("testuser").toString()), e.getMessage());
        verify(mockRepo1, never()).getHttpTransportUrl();
    }

    @Test
    void testShowBackupStatus_NoBackups() {
        // Create a new BackupService with an empty directory
        String emptyDir = tempDir.resolve("empty").toString();
        BackupService service = new BackupService(gitHubService, emptyDir);
        
        assertDoesNotThrow(() -> service.showBackupStatus());
    }

    @Test
    void testShowBackupStatus_WithBackups() throws IOException {
        // Create a fake backup structure
        Path userDir = tempDir.resolve("testuser");
        Path repoDir = userDir.resolve("test-repo");
        Files.createDirectories(repoDir);

        assertDoesNotThrow(() -> backupService.showBackupStatus());
    }

    @Test
    void testBackupDirectory_IsAbsolute() throws IOException {
        BackupService service = new BackupService(gitHubService, "relative/path");
        
        // The service should convert relative paths to absolute
        assertDoesNotThrow(() -> service.showBackupStatus());
    }

    @Test
    void testBackupUserRepositories_HandlesException() throws IOException {
        when(gitHubService.getPublicRepositories("erroruser"))
                .thenThrow(new IOException("Test exception"));

        assertThrows(IOException.class, () -> backupService.backupUserRepositories("erroruser"));
    }

    @Test
    void testGetBackupStatusData_NoBackups() {
        // Test with empty directory
        BackupStatusResponse status = backupService.getBackupStatusData();
        
        assertNotNull(status);
        assertEquals(0, status.getTotalUsers());
        assertEquals(0, status.getTotalRepositories());
        assertNotNull(status.getUsers());
        assertTrue(status.getUsers().isEmpty());
    }

    @Test
    void testGetBackupStatusData_WithBackups() throws IOException {
        // Create a fake backup structure with multiple users
        Path user1Dir = tempDir.resolve("user1");
        Path repo1Dir = user1Dir.resolve("repo1");
        Path repo2Dir = user1Dir.resolve("repo2");
        Files.createDirectories(repo1Dir);
        Files.createDirectories(repo2Dir);

        Path user2Dir = tempDir.resolve("user2");
        Path repo3Dir = user2Dir.resolve("repo3");
        Files.createDirectories(repo3Dir);

        BackupStatusResponse status = backupService.getBackupStatusData();
        
        assertNotNull(status);
        assertEquals(2, status.getTotalUsers());
        assertEquals(3, status.getTotalRepositories());
        assertNotNull(status.getUsers());
        assertEquals(2, status.getUsers().size());
        
        // Verify first user
        BackupStatusResponse.UserBackupInfo user1 = status.getUsers().stream()
                .filter(u -> "user1".equals(u.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(user1);
        assertEquals(2, user1.getRepositoryCount());
        assertEquals(2, user1.getRepositories().size());
        
        // Verify second user
        BackupStatusResponse.UserBackupInfo user2 = status.getUsers().stream()
                .filter(u -> "user2".equals(u.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(user2);
        assertEquals(1, user2.getRepositoryCount());
        assertEquals(1, user2.getRepositories().size());
    }

    @Test
    void testGetBackupStatusData_NonExistentDirectory() {
        // Test with non-existent directory
        BackupService service = new BackupService(gitHubService, tempDir.resolve("nonexistent").toString());
        
        BackupStatusResponse status = service.getBackupStatusData();
        
        assertNotNull(status);
        assertEquals(0, status.getTotalUsers());
        assertEquals(0, status.getTotalRepositories());
        assertTrue(status.getUsers().isEmpty());
    }

    @Test
    void testGetBackupStatusData_IgnoresFilesAndFormatsLastModified() throws IOException {
        Path repoDir = Files.createDirectories(tempDir.resolve("user1").resolve("repo1"));
        Files.createFile(tempDir.resolve("stray-file.txt"));
        Files.createFile(tempDir.resolve("user1").resolve("README.txt"));
        long lastModified = 1_700_000_000_000L;
        assertTrue(repoDir.toFile().setLastModified(lastModified));

        BackupStatusResponse status = backupService.getBackupStatusData();

        assertEquals(1, status.getTotalUsers());
        assertEquals(1, status.getTotalRepositories());
        BackupStatusResponse.RepositoryInfo repo = status.getUsers().get(0).getRepositories().get(0);
        assertEquals("repo1", repo.getName());
        String expected = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochMilli(lastModified));
        assertEquals(expected, repo.getLastUpdated());
    }

    @Test
    void testShowBackupStatus_NonExistentDirectoryMessage() {
        Path missing = tempDir.resolve("nonexistent");
        BackupService service = new BackupService(gitHubService, missing.toString());

        String output = captureStdout(service::showBackupStatus);

        assertTrue(output.contains("No backups found. Backup directory does not exist: " + missing), output);
    }

    @Test
    void testShowBackupStatus_EmptyDirectoryMessage() {
        String output = captureStdout(backupService::showBackupStatus);

        assertEquals("No backups found.", output.trim());
    }

    @Test
    void testShowBackupStatus_ListsUsersRepositoriesAndTotals() throws IOException {
        Files.createDirectories(tempDir.resolve("user1").resolve("repo1"));
        Files.createDirectories(tempDir.resolve("user1").resolve("repo2"));
        Files.createDirectories(tempDir.resolve("user2").resolve("repo3"));

        String output = captureStdout(backupService::showBackupStatus);

        assertTrue(output.contains("Backup directory: " + tempDir.toAbsolutePath()), output);
        assertTrue(output.contains("user1/ (2 repositories)"), output);
        assertTrue(output.contains("user2/ (1 repositories)"), output);
        assertTrue(output.contains("  - repo3 (last updated: "), output);
        assertTrue(output.contains("Total: 2 users/organizations, 3 repositories"), output);
    }

    private Path createSourceRepository(String name) throws Exception {
        Path source = tempDir.resolve("sources").resolve(name);
        try (Git git = Git.init().setDirectory(source.toFile()).call()) {
            commitFile(git, "README.md", "initial");
        }
        return source;
    }

    private static RevCommit commitFile(Git git, String fileName, String content) throws Exception {
        File workTree = git.getRepository().getWorkTree();
        Files.writeString(workTree.toPath().resolve(fileName), content);
        git.add().addFilepattern(fileName).call();
        PersonIdent ident = new PersonIdent("Test", "test@example.com");
        return git.commit().setMessage("Add " + fileName).setAuthor(ident).setCommitter(ident).call();
    }

    private static String captureStdout(ThrowingRunnable action) {
        PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
        try {
            action.run();
        } catch (Exception e) {
            throw new AssertionError(e);
        } finally {
            System.setOut(original);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
