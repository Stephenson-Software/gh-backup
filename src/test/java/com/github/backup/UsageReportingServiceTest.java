package com.github.backup;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Drives the usage-reporting wiring against a stub trace server on a loopback
 * port (the JDK's own), so nothing here ever reaches the real service.
 */
class UsageReportingServiceTest {

    private HttpServer server;
    private final List<String> bodies = new CopyOnWriteArrayList<>();
    private final List<String> authorizations = new CopyOnWriteArrayList<>();
    private volatile CountDownLatch arrived = new CountDownLatch(1);

    private Logger logger;
    private ListAppender<ILoggingEvent> logAppender;

    @TempDir
    Path tempDir;

    @BeforeEach
    void startStubAndCaptureLog() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            bodies.add(new String(readAll(exchange.getRequestBody()), StandardCharsets.UTF_8));
            authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.sendResponseHeaders(201, -1);
            exchange.close();
            arrived.countDown();
        });
        server.start();

        logger = (Logger) LoggerFactory.getLogger(UsageReportingService.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
    }

    @AfterEach
    void stop() {
        server.stop(0);
        logger.detachAppender(logAppender);
    }

    private String endpoint() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private Path marker() {
        return tempDir.resolve(".config").resolve("gh-backup").resolve(UsageReportingService.NOTICE_MARKER_FILE);
    }

    private long noticesLogged() {
        return logAppender.list.stream()
                .filter(event -> event.getFormattedMessage().startsWith("Usage reporting is on: gh-backup sends its name, version and a random installation ID"))
                .count();
    }

    @Test
    void carriesARandomInstallationIdOnlyWhileReportingIsOn() {
        UsageReportingService on = new UsageReportingService("true", endpoint(), "test-key", "1.0", marker());
        UsageReportingService off = new UsageReportingService("false", endpoint(), "test-key", "1.0", marker());

        assertNotNull(on.installId(), "an enabled client carries a random installation ID");
        assertNull(off.installId(), "a disabled client never makes up an ID");
        assertTrue(Files.exists(marker().resolveSibling("trace-install-id")), "kept next to the notice marker");
        on.close();
        off.close();
    }

    @Test
    void startupEventCarriesTheApplicationNameAndVersionOnly() throws Exception {
        UsageReportingService service = new UsageReportingService("true", endpoint(), "test-key", "2.0.0-TEST", marker());
        service.start();

        assertTrue(arrived.await(5, TimeUnit.SECONDS), "startup event should arrive");
        service.close();

        assertEquals(1, bodies.size());
        assertEquals("{\"application\":\"gh-backup\",\"name\":\"startup\",\"tags\":{\"version\":\"2.0.0-TEST\",\"install\":\"" + service.installId() + "\"}}", bodies.get(0));
        assertEquals("Bearer test-key", authorizations.get(0));
    }

    @Test
    void backupCompletedEventCarriesTheVersionOnly() throws Exception {
        UsageReportingService service = new UsageReportingService("true", endpoint(), "test-key", "2.0.0-TEST", marker());
        service.start();
        assertTrue(arrived.await(5, TimeUnit.SECONDS));

        arrived = new CountDownLatch(1);
        service.backupCompleted();
        assertTrue(arrived.await(5, TimeUnit.SECONDS), "backup-completed event should arrive");
        service.close();

        assertEquals("{\"application\":\"gh-backup\",\"name\":\"backup-completed\",\"tags\":{\"version\":\"2.0.0-TEST\",\"install\":\"" + service.installId() + "\"}}", bodies.get(1));
    }

    @Test
    void unfilteredVersionPlaceholderIsSentAsUnknown() throws Exception {
        UsageReportingService service = new UsageReportingService("true", endpoint(), "test-key", "@project.version@", marker());
        service.start();

        assertTrue(arrived.await(5, TimeUnit.SECONDS));
        service.close();

        assertEquals("{\"application\":\"gh-backup\",\"name\":\"startup\",\"tags\":{\"version\":\"unknown\",\"install\":\"" + service.installId() + "\"}}", bodies.get(0));
    }

    @Test
    void firstRunNoticeIsLoggedOnceAndRecordedInTheMarkerFile() throws Exception {
        assertFalse(Files.exists(marker()));

        UsageReportingService first = new UsageReportingService("true", endpoint(), "test-key", "2.0.0-TEST", marker());
        first.start();
        first.close();

        assertEquals(1, noticesLogged(), "the notice should be logged on the first run");
        assertTrue(Files.exists(marker()), "the marker file should record that the notice was shown");
        String notice = logAppender.list.get(0).getFormattedMessage();
        assertTrue(notice.contains("trace.danielstephenson.dev"), notice);
        assertTrue(notice.contains("-Dusage.reporting.enabled=false"), notice);
        assertTrue(notice.contains("TRACE_USAGE_REPORTING=off"), notice);
        assertTrue(notice.contains("https://danielstephenson.dev/usage-reporting"), notice);

        UsageReportingService second = new UsageReportingService("true", endpoint(), "test-key", "2.0.0-TEST", marker());
        second.start();
        second.close();

        assertEquals(1, noticesLogged(), "the notice should not be logged again on the second run");
    }

    @Test
    void disabledSendsNothingAndShowsNoNotice() throws Exception {
        UsageReportingService service = new UsageReportingService("false", endpoint(), "test-key", "2.0.0-TEST", marker());
        service.start();
        service.backupCompleted();
        service.close();

        assertFalse(service.isEnabled());
        assertFalse(arrived.await(300, TimeUnit.MILLISECONDS), "nothing should be sent when disabled");
        assertTrue(bodies.isEmpty());
        assertEquals(0, noticesLogged());
        assertFalse(Files.exists(marker()), "no marker should be written when reporting is off");
    }

    @Test
    void blankEnabledValueMeansOnAndMissingKeyMeansOff() {
        assertTrue(new UsageReportingService("", endpoint(), "test-key", "1", marker()).isEnabled(),
                "an empty USAGE_REPORTING_ENABLED must not turn reporting off or crash");
        assertTrue(new UsageReportingService(null, endpoint(), "test-key", "1", marker()).isEnabled());
        assertFalse(new UsageReportingService("true", endpoint(), "", "1", marker()).isEnabled(),
                "no key means nothing can be reported");
        assertFalse(new UsageReportingService("FALSE", endpoint(), "test-key", "1", marker()).isEnabled());
    }

    @Test
    void badEndpointOrUnwritableMarkerNeverThrows() {
        UsageReportingService badEndpoint = new UsageReportingService("true", "   ", "test-key", "1", marker());
        assertFalse(badEndpoint.isEnabled());
        assertDoesNotThrow(badEndpoint::start);
        assertDoesNotThrow(badEndpoint::backupCompleted);
        assertDoesNotThrow(badEndpoint::close);

        // A marker whose parent is a regular file cannot be created; the notice is simply shown again next time.
        Path notADirectory = tempDir.resolve("not-a-directory");
        assertDoesNotThrow(() -> Files.writeString(notADirectory, "x"));
        UsageReportingService unwritable = new UsageReportingService("true", endpoint(), "test-key", "1", notADirectory.resolve("marker"));
        assertDoesNotThrow(unwritable::start);
        assertDoesNotThrow(unwritable::close);
        assertEquals(1, noticesLogged());
    }

    @Test
    void defaultMarkerLivesUnderTheUsersConfigDirectory() {
        Path marker = UsageReportingService.defaultNoticeMarker();
        assertNotNull(marker);
        assertTrue(marker.endsWith(Path.of(".config", "gh-backup", UsageReportingService.NOTICE_MARKER_FILE)), marker.toString());
    }

    private static byte[] readAll(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }
}
