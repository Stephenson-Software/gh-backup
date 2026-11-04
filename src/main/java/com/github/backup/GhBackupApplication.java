package com.github.backup;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class GhBackupApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(GhBackupApplication.class, args);
        // Web and daemon modes keep running; a command-line run has finished and sets the exit status
        if (isCommandLineRun(context)) {
            System.exit(SpringApplication.exit(context));
        }
    }

    static boolean isCommandLineRun(ApplicationContext context) {
        return context.getBeanNamesForType(BackupCommandLineRunner.class).length > 0;
    }
}
