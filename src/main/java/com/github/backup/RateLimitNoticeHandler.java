package com.github.backup;

import org.kohsuke.github.GitHubRateLimitHandler;
import org.kohsuke.github.RateLimitHandler;
import org.kohsuke.github.connector.GitHubConnectorResponse;

import java.io.IOException;
import java.io.PrintStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Says so when the GitHub rate limit is reached, then waits for it to reset as the
 * client would by default. The wait can last up to an hour and the client is silent
 * throughout, so without this a backup that has hit the limit looks hung.
 */
class RateLimitNoticeHandler extends GitHubRateLimitHandler {

    private static final String RESET_HEADER = "X-RateLimit-Reset";
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final boolean authenticated;
    private final PrintStream out;
    private final GitHubRateLimitHandler wait;

    RateLimitNoticeHandler(boolean authenticated) {
        this(authenticated, System.err, RateLimitHandler.WAIT);
    }

    RateLimitNoticeHandler(boolean authenticated, PrintStream out, GitHubRateLimitHandler wait) {
        this.authenticated = authenticated;
        this.out = out;
        this.wait = wait;
    }

    @Override
    public void onError(GitHubConnectorResponse response) throws IOException {
        String notice = "GitHub API rate limit reached. Waiting " + describeReset(response.header(RESET_HEADER))
                + "; the backup will then continue by itself.";
        out.println(authenticated ? notice : notice + " Setting GITHUB_TOKEN raises the limit.");
        wait.onError(response);
    }

    private static String describeReset(String resetHeader) {
        try {
            return "until about " + TIME_FORMAT.format(Instant.ofEpochSecond(Long.parseLong(resetHeader)));
        } catch (NumberFormatException e) {
            return "for it to reset";
        }
    }
}
