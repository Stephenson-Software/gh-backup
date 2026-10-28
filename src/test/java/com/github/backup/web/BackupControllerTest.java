package com.github.backup.web;

import com.github.backup.BackupService;
import com.github.backup.UsageReportingService;
import com.github.backup.web.BackupStatusResponse.RepositoryInfo;
import com.github.backup.web.BackupStatusResponse.UserBackupInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BackupController.class)
@ActiveProfiles("web")
class BackupControllerTest {

    private static final String REQUIRED_MESSAGE = "User or organization name is required";
    private static final String INVALID_FORMAT_MESSAGE = "Invalid GitHub username format";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BackupService backupService;

    @MockBean
    private UsageReportingService usageReporting;

    @Test
    void createBackup_Success() throws Exception {
        doNothing().when(backupService).backupUserRepositories(anyString());

        mockMvc.perform(post("/api/backups")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userOrOrg\":\"testuser\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Backup completed successfully for testuser"));

        verify(backupService, times(1)).backupUserRepositories("testuser");
    }

    @Test
    void createBackup_Failure() throws Exception {
        doThrow(new IOException("Test error")).when(backupService).backupUserRepositories(anyString());

        mockMvc.perform(post("/api/backups")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userOrOrg\":\"testuser\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Error: Test error"));

        verify(backupService, times(1)).backupUserRepositories("testuser");
        verify(usageReporting, never()).backupCompleted();
    }

    @Test
    void createBackup_Success_ReportsBackupCompleted() throws Exception {
        mockMvc.perform(post("/api/backups")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userOrOrg\":\"testuser\"}"))
                .andExpect(status().isOk());

        verify(usageReporting, times(1)).backupCompleted();
    }

    @Test
    void createBackup_MissingUserOrOrg() throws Exception {
        assertRejected("{}", REQUIRED_MESSAGE);
    }

    @Test
    void createBackup_BlankUserOrOrg() throws Exception {
        assertRejected("{\"userOrOrg\":\"   \"}", REQUIRED_MESSAGE);
    }

    @Test
    void createBackup_EmptyUserOrOrg() throws Exception {
        assertRejected("{\"userOrOrg\":\"\"}", REQUIRED_MESSAGE);
    }

    @Test
    void createBackup_LeadingHyphen() throws Exception {
        assertRejected("{\"userOrOrg\":\"-octocat\"}", INVALID_FORMAT_MESSAGE);
    }

    @Test
    void createBackup_TrailingHyphen() throws Exception {
        assertRejected("{\"userOrOrg\":\"octocat-\"}", INVALID_FORMAT_MESSAGE);
    }

    @Test
    void createBackup_ConsecutiveHyphens() throws Exception {
        assertRejected("{\"userOrOrg\":\"octo--cat\"}", INVALID_FORMAT_MESSAGE);
    }

    @Test
    void createBackup_InvalidCharacters() throws Exception {
        assertRejected("{\"userOrOrg\":\"../etc\"}", INVALID_FORMAT_MESSAGE);
    }

    @Test
    void createBackup_NameTooLong() throws Exception {
        assertRejected("{\"userOrOrg\":\"" + "a".repeat(40) + "\"}", INVALID_FORMAT_MESSAGE);
    }

    @Test
    void createBackup_MaximumLengthName() throws Exception {
        String name = "a".repeat(39);

        mockMvc.perform(post("/api/backups")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userOrOrg\":\"" + name + "\"}"))
                .andExpect(status().isOk());

        verify(backupService, times(1)).backupUserRepositories(name);
    }

    @Test
    void createBackup_SingleHyphenBetweenCharacters() throws Exception {
        mockMvc.perform(post("/api/backups")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userOrOrg\":\"spring-projects\"}"))
                .andExpect(status().isOk());

        verify(backupService, times(1)).backupUserRepositories("spring-projects");
    }

    private void assertRejected(String body, String expectedMessage) throws Exception {
        mockMvc.perform(post("/api/backups")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(expectedMessage));

        verify(backupService, never()).backupUserRepositories(anyString());
        verify(usageReporting, never()).backupCompleted();
    }

    @Test
    void getStatus_WithData() throws Exception {
        RepositoryInfo repo1 = new RepositoryInfo("repo1", "2026-01-10 12:00:00");
        RepositoryInfo repo2 = new RepositoryInfo("repo2", "2026-01-10 12:30:00");
        UserBackupInfo user = new UserBackupInfo("testuser", 2, Arrays.asList(repo1, repo2));
        BackupStatusResponse status = new BackupStatusResponse(1, 2, Collections.singletonList(user));

        when(backupService.getBackupStatusData()).thenReturn(status);

        mockMvc.perform(get("/api/backups/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").value(1))
                .andExpect(jsonPath("$.totalRepositories").value(2))
                .andExpect(jsonPath("$.users[0].name").value("testuser"))
                .andExpect(jsonPath("$.users[0].repositoryCount").value(2))
                .andExpect(jsonPath("$.users[0].repositories[0].name").value("repo1"));

        verify(backupService, times(1)).getBackupStatusData();
    }

    @Test
    void getStatus_NoData() throws Exception {
        BackupStatusResponse status = new BackupStatusResponse(0, 0, Collections.emptyList());

        when(backupService.getBackupStatusData()).thenReturn(status);

        mockMvc.perform(get("/api/backups/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").value(0))
                .andExpect(jsonPath("$.totalRepositories").value(0))
                .andExpect(jsonPath("$.users").isEmpty());

        verify(backupService, times(1)).getBackupStatusData();
    }

    @Test
    void getStatus_ServiceError() throws Exception {
        when(backupService.getBackupStatusData()).thenThrow(new RuntimeException("Test error"));

        mockMvc.perform(get("/api/backups/status"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.totalUsers").value(0))
                .andExpect(jsonPath("$.totalRepositories").value(0));

        verify(backupService, times(1)).getBackupStatusData();
    }
}
