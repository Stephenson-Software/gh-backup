package com.github.backup;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * docker-compose.yml passes only the variables it lists into the container, so a variable
 * that .env.example invites users to set but the compose file does not forward is silently
 * ignored. Neither {@code mvn verify} nor the image build would otherwise notice.
 */
class DockerComposeEnvironmentTest {

    private static final Pattern ENV_EXAMPLE_ENTRY = Pattern.compile("^([A-Z][A-Z0-9_]*)=");

    @Test
    void testEnvExampleVariables_ForwardedByDockerCompose() throws Exception {
        String compose = Files.readString(Path.of("docker-compose.yml"));
        List<String> variables = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        for (String line : Files.readAllLines(Path.of(".env.example"))) {
            Matcher matcher = ENV_EXAMPLE_ENTRY.matcher(line);
            if (matcher.find()) {
                String name = matcher.group(1);
                variables.add(name);
                if (!compose.contains("- " + name + "=${" + name + ":-")) {
                    missing.add(name);
                }
            }
        }

        assertTrue(variables.contains("GITHUB_TOKEN"), "expected .env.example to list GITHUB_TOKEN, found " + variables);
        assertEquals(List.of(), missing,
                "these .env.example variables are not passed through by docker-compose.yml");
    }
}
