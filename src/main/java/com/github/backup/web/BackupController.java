package com.github.backup.web;

import com.github.backup.BackupService;
import com.github.backup.UsageReportingService;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;

/**
 * REST controller for backup management operations.
 * 
 * Note on CSRF: CSRF protection is not required for this API because:
 * 1. The API is designed to be consumed by the same-origin frontend
 * 2. Spring Boot disables CSRF by default for stateless REST APIs
 * 3. No session state is maintained between requests
 */
@RestController
@RequestMapping("/api/backups")
public class BackupController {

    private final BackupService backupService;
    private final UsageReportingService usageReporting;

    public BackupController(BackupService backupService, UsageReportingService usageReporting) {
        this.backupService = backupService;
        this.usageReporting = usageReporting;
    }

    @PostMapping
    public ResponseEntity<BackupResponse> createBackup(@Valid @RequestBody BackupRequest request) {
        try {
            backupService.backupUserRepositories(request.getUserOrOrg());
            usageReporting.backupCompleted();
            return ResponseEntity.ok(new BackupResponse(true, "Backup completed successfully for " + request.getUserOrOrg()));
        } catch (IOException e) {
            return ResponseEntity.status(500)
                    .body(new BackupResponse(false, "Error: " + e.getMessage()));
        }
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<BackupResponse> handleInvalidRequest(MethodArgumentNotValidException e) {
        // A blank name also fails the format pattern; report it as missing rather than malformed
        String message = e.getBindingResult().getFieldErrors().stream()
                .sorted(Comparator.comparing((FieldError error) -> !"NotBlank".equals(error.getCode())))
                .map(FieldError::getDefaultMessage)
                .findFirst()
                .orElse("Invalid request");
        return ResponseEntity.badRequest().body(new BackupResponse(false, message));
    }

    @GetMapping("/status")
    public ResponseEntity<BackupStatusResponse> getStatus() {
        try {
            BackupStatusResponse status = backupService.getBackupStatusData();
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(new BackupStatusResponse(0, 0, Collections.emptyList()));
        }
    }
}
