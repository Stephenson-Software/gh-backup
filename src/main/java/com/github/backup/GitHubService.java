package com.github.backup;

import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.HttpException;
import org.springframework.stereotype.Service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class GitHubService {

    private final GitHub github;

    public GitHubService() throws IOException {
        this(System.getenv("GITHUB_TOKEN"));
    }

    GitHubService(String token) throws IOException {
        this.github = connect(token);
    }

    private static GitHub connect(String token) throws IOException {
        if (token == null || token.isEmpty()) {
            return connectAnonymously();
        }

        GitHub authenticated = new GitHubBuilder().withOAuthToken(token).build();
        try {
            // Validate that the provided token is valid and has access
            authenticated.getMyself();
            System.out.println("Connected to GitHub with authentication");
            return authenticated;
        } catch (IOException e) {
            if (isUnauthorized(e)) {
                System.err.println("Warning: Invalid or expired GITHUB_TOKEN detected. Falling back to anonymous GitHub access.");
                return connectAnonymously();
            }
            // Only a 401 says the token is bad. The client lasts as long as the process, so
            // dropping the token over a passing failure would leave a daemon anonymous until restarted
            System.err.println("Warning: Could not verify GITHUB_TOKEN, continuing with it: " + e.getMessage());
            return authenticated;
        }
    }

    private static GitHub connectAnonymously() throws IOException {
        GitHub anonymous = GitHub.connectAnonymously();
        System.out.println("Connected to GitHub anonymously (rate limits apply)");
        return anonymous;
    }

    private static boolean isUnauthorized(IOException e) {
        return e instanceof HttpException httpException
                && httpException.getResponseCode() == HttpURLConnection.HTTP_UNAUTHORIZED;
    }

    public List<GHRepository> getPublicRepositories(String userOrOrg) throws IOException {
        try {
            // Try as organization first
            return github.getOrganization(userOrOrg).listRepositories().toList()
                    .stream()
                    .filter(repo -> !repo.isPrivate())
                    .collect(Collectors.toList());
        } catch (IOException notAnOrganization) {
            // Fall through to the user lookup, which reports the failure if it fails too
        }

        try {
            // If not an organization, try as user
            return github.getUser(userOrOrg).listRepositories().toList()
                    .stream()
                    .filter(repo -> !repo.isPrivate())
                    .collect(Collectors.toList());
        } catch (FileNotFoundException notFound) {
            // github-api raises this for a 404; any other failure (rate limit, network, etc.)
            // propagates with its original error
            throw new IOException(
                "'" + userOrOrg + "' not found as GitHub organization or user. " +
                "Please verify the name is correct.",
                notFound
            );
        }
    }
}
