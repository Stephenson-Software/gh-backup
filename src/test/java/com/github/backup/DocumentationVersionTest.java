package com.github.backup;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The documentation and Dockerfile spell out the built JAR's file name, which embeds the
 * project version. A version bump that misses one of them leaves a copy-pasted command
 * (or, for the Dockerfile, the image build) pointing at a file that does not exist, and
 * {@code mvn verify} would not otherwise notice. CHANGELOG.md is deliberately excluded:
 * it records the names of past artifacts.
 */
class DocumentationVersionTest {

    private static final List<String> FILES_NAMING_THE_JAR = List.of(
            "README.md", "USER_GUIDE.md", "CONFIG.md", "COMMANDS.md", "Dockerfile");

    private static final Pattern VERSIONED_JAR = Pattern.compile("gh-backup-([0-9][^\\s/`)]*)\\.jar");

    @Test
    void testDocumentedJarNames_MatchPomVersion() throws Exception {
        String version = projectVersion();
        List<String> stale = new ArrayList<>();
        int references = 0;

        for (String file : FILES_NAMING_THE_JAR) {
            List<String> lines = Files.readAllLines(Path.of(file));
            for (int i = 0; i < lines.size(); i++) {
                Matcher matcher = VERSIONED_JAR.matcher(lines.get(i));
                while (matcher.find()) {
                    references++;
                    if (!matcher.group(1).equals(version)) {
                        stale.add(file + ":" + (i + 1) + " names " + matcher.group());
                    }
                }
            }
        }

        assertTrue(references > 0, "expected at least one versioned JAR name in " + FILES_NAMING_THE_JAR);
        assertEquals(List.of(), stale,
                "these references do not match gh-backup-" + version + ".jar from pom.xml");
    }

    @Test
    void testVersionedJarPattern_IgnoresGlob() {
        assertFalse(VERSIONED_JAR.matcher("files: target/gh-backup-*.jar").find());
    }

    private static String projectVersion() throws Exception {
        Document pom = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new File("pom.xml"));
        for (Node node = pom.getDocumentElement().getFirstChild(); node != null; node = node.getNextSibling()) {
            if ("version".equals(node.getNodeName())) {
                return node.getTextContent().trim();
            }
        }
        throw new IOException("pom.xml has no top-level <version>");
    }
}
