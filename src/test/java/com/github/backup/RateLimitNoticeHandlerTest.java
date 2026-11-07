package com.github.backup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GitHubRateLimitHandler;
import org.kohsuke.github.connector.GitHubConnectorResponse;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RateLimitNoticeHandlerTest {

    private static final long RESET_EPOCH_SECONDS = 1768050312L;

    @Mock
    private GitHubRateLimitHandler wait;

    @Mock
    private GitHubConnectorResponse response;

    @Mock
    private PrintStream mockOut;

    private ByteArrayOutputStream output;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        output = new ByteArrayOutputStream();
    }

    private RateLimitNoticeHandler handler(boolean authenticated) {
        return new RateLimitNoticeHandler(authenticated, new PrintStream(output, true), wait);
    }

    @Test
    void testOnError_NamesResetTimeInLocalZone() throws IOException {
        when(response.header("X-RateLimit-Reset")).thenReturn(Long.toString(RESET_EPOCH_SECONDS));

        handler(true).onError(response);

        String expected = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochSecond(RESET_EPOCH_SECONDS));
        assertTrue(output.toString().contains("rate limit reached"), output.toString());
        assertTrue(output.toString().contains("until about " + expected), output.toString());
    }

    @Test
    void testOnError_MissingResetHeader() throws IOException {
        handler(true).onError(response);

        assertTrue(output.toString().contains("Waiting for it to reset"), output.toString());
    }

    @Test
    void testOnError_UnreadableResetHeader() throws IOException {
        when(response.header("X-RateLimit-Reset")).thenReturn("soon");

        handler(true).onError(response);

        assertTrue(output.toString().contains("Waiting for it to reset"), output.toString());
    }

    @Test
    void testOnError_AnonymousSuggestsToken() throws IOException {
        handler(false).onError(response);

        assertTrue(output.toString().contains("Setting GITHUB_TOKEN raises the limit."), output.toString());
    }

    @Test
    void testOnError_AuthenticatedDoesNotSuggestToken() throws IOException {
        handler(true).onError(response);

        assertFalse(output.toString().contains("GITHUB_TOKEN"), output.toString());
    }

    @Test
    void testOnError_PrintsNoticeBeforeWaiting() throws IOException {
        new RateLimitNoticeHandler(true, mockOut, wait).onError(response);

        InOrder inOrder = inOrder(mockOut, wait);
        inOrder.verify(mockOut).println(anyString());
        inOrder.verify(wait).onError(response);
    }

    @Test
    void testOnError_FailedWaitPropagates() throws IOException {
        IOException failure = new IOException("wait interrupted");
        doThrow(failure).when(wait).onError(response);

        IOException thrown = assertThrows(IOException.class, () -> handler(true).onError(response));

        assertSame(failure, thrown);
    }
}
