package com.github.backup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.HttpException;
import org.kohsuke.github.PagedIterable;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GitHubServiceTest {

    @Mock
    private GitHub github;

    @Mock
    private GitHub authenticatedGithub;

    @Mock
    private GHOrganization organization;

    @Mock
    private GHUser user;

    @Mock
    private PagedIterable<GHRepository> pagedRepositories;

    @Mock
    private GHRepository publicRepo;

    @Mock
    private GHRepository privateRepo;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(publicRepo.isPrivate()).thenReturn(false);
        when(privateRepo.isPrivate()).thenReturn(true);
    }

    @Test
    void testGitHubServiceInitialization_Anonymous() {
        assertDoesNotThrow(() -> {
            GitHubService service = new GitHubService();
            assertNotNull(service);
        });
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "GITHUB_TOKEN", matches = ".+")
    void testGitHubServiceInitialization_WithToken() {
        assertDoesNotThrow(() -> {
            GitHubService service = new GitHubService();
            assertNotNull(service);
        });
    }

    @Test
    void testGetPublicRepositories_InvalidUser() throws IOException {
        GitHubService service = new GitHubService();

        // Test with a very unlikely username
        assertThrows(IOException.class, () ->
            service.getPublicRepositories("this-user-definitely-does-not-exist-12345678")
        );
    }

    @Test
    void testGetPublicRepositories_OrganizationFiltersPrivateRepositories() throws IOException {
        when(github.getOrganization("test-org")).thenReturn(organization);
        when(organization.listRepositories()).thenReturn(pagedRepositories);
        when(pagedRepositories.toList()).thenReturn(Arrays.asList(publicRepo, privateRepo));

        List<GHRepository> repos = newServiceBackedBy(github).getPublicRepositories("test-org");

        assertEquals(List.of(publicRepo), repos);
        verify(github, never()).getUser(anyString());
    }

    @Test
    void testGetPublicRepositories_FallsBackToUserWhenNotAnOrganization() throws IOException {
        when(github.getOrganization("test-user")).thenThrow(new IOException("not an org"));
        when(github.getUser("test-user")).thenReturn(user);
        when(user.listRepositories()).thenReturn(pagedRepositories);
        when(pagedRepositories.toList()).thenReturn(Arrays.asList(privateRepo, publicRepo));

        List<GHRepository> repos = newServiceBackedBy(github).getPublicRepositories("test-user");

        assertEquals(List.of(publicRepo), repos);
    }

    @Test
    void testGetPublicRepositories_NotFoundAsOrganizationOrUser() throws IOException {
        // The status is not part of the message of a real 404, which may not contain the digits at all
        IOException userNotFound = new GHFileNotFoundException("https://api.github.com/users/missing {\"message\":\"Not Found\"}");
        when(github.getOrganization("missing")).thenThrow(new IOException("org lookup failed"));
        when(github.getUser("missing")).thenThrow(userNotFound);

        GitHubService service = newServiceBackedBy(github);
        IOException thrown = assertThrows(IOException.class, () -> service.getPublicRepositories("missing"));

        assertEquals("'missing' not found as GitHub organization or user. Please verify the name is correct.",
                thrown.getMessage());
        assertSame(userNotFound, thrown.getCause());
    }

    @Test
    void testGetPublicRepositories_NonNotFoundErrorIsRethrownUnchanged() throws IOException {
        IOException rateLimited = new IOException("API rate limit exceeded");
        when(github.getOrganization("test-user")).thenThrow(new IOException("org lookup failed"));
        when(github.getUser("test-user")).thenThrow(rateLimited);

        GitHubService service = newServiceBackedBy(github);
        IOException thrown = assertThrows(IOException.class, () -> service.getPublicRepositories("test-user"));

        assertSame(rateLimited, thrown);
    }

    @Test
    void testGetPublicRepositories_NullErrorMessageIsRethrownUnchanged() throws IOException {
        IOException noMessage = new IOException();
        when(github.getOrganization("test-user")).thenThrow(new IOException("org lookup failed"));
        when(github.getUser("test-user")).thenThrow(noMessage);

        GitHubService service = newServiceBackedBy(github);
        IOException thrown = assertThrows(IOException.class, () -> service.getPublicRepositories("test-user"));

        assertSame(noMessage, thrown);
    }

    @Test
    void testGetPublicRepositories_FailureWhoseUrlContains404IsNotReportedAsNotFound() throws IOException {
        // A connection failure's message carries the URL, and so the name being looked up
        IOException connectionFailed = new HttpException(
                "Server returned HTTP response code: -1, message: 'null' for URL: https://api.github.com/users/team404",
                -1, null, "https://api.github.com/users/team404");
        when(github.getOrganization("team404")).thenThrow(new IOException("org lookup failed"));
        when(github.getUser("team404")).thenThrow(connectionFailed);

        GitHubService service = newServiceBackedBy(github);
        IOException thrown = assertThrows(IOException.class, () -> service.getPublicRepositories("team404"));

        assertSame(connectionFailed, thrown);
    }

    @Test
    void testGitHubService_RejectedTokenFallsBackToAnonymous() throws IOException {
        when(authenticatedGithub.getMyself()).thenThrow(httpFailure(401));
        stubRepositoriesFor(github);

        CapturedConstruction result = constructWithToken("rejected-token");

        assertEquals(List.of(publicRepo), result.service.getPublicRepositories("test-org"));
        verify(authenticatedGithub, never()).getOrganization(anyString());
        assertTrue(result.errorOutput.contains("Invalid or expired GITHUB_TOKEN"), result.errorOutput);
    }

    @Test
    void testGitHubService_ServerErrorKeepsToken() throws IOException {
        assertTokenKeptAfter(httpFailure(500));
    }

    @Test
    void testGitHubService_ForbiddenKeepsToken() throws IOException {
        assertTokenKeptAfter(httpFailure(403));
    }

    @Test
    void testGitHubService_ConnectionFailureKeepsToken() throws IOException {
        assertTokenKeptAfter(httpFailure(-1));
    }

    @Test
    void testGitHubService_NonHttpFailureKeepsToken() throws IOException {
        assertTokenKeptAfter(new IOException("stub failure"));
    }

    @Test
    void testGitHubService_ValidTokenIsUsed() throws IOException {
        stubRepositoriesFor(authenticatedGithub);

        CapturedConstruction result = constructWithToken("valid-token");

        assertEquals(List.of(publicRepo), result.service.getPublicRepositories("test-org"));
        assertEquals("", result.errorOutput);
    }

    @Test
    void testGitHubService_EmptyTokenConnectsAnonymously() throws IOException {
        stubRepositoriesFor(github);

        CapturedConstruction result = constructWithToken("");

        assertEquals(List.of(publicRepo), result.service.getPublicRepositories("test-org"));
        verifyNoInteractions(authenticatedGithub);
    }

    private void assertTokenKeptAfter(IOException failure) throws IOException {
        when(authenticatedGithub.getMyself()).thenThrow(failure);
        stubRepositoriesFor(authenticatedGithub);

        CapturedConstruction result = constructWithToken("unverified-token");

        assertEquals(List.of(publicRepo), result.service.getPublicRepositories("test-org"));
        verifyNoInteractions(github);
        assertTrue(result.errorOutput.contains("Could not verify GITHUB_TOKEN"), result.errorOutput);
        assertTrue(result.errorOutput.contains(failure.getMessage()), result.errorOutput);
        assertFalse(result.errorOutput.contains("Invalid or expired"), result.errorOutput);
        assertFalse(result.errorOutput.contains("unverified-token"), result.errorOutput);
    }

    private static HttpException httpFailure(int responseCode) {
        return new HttpException("stub failure " + responseCode, responseCode, null, "https://api.github.com/user");
    }

    private void stubRepositoriesFor(GitHub client) throws IOException {
        when(client.getOrganization("test-org")).thenReturn(organization);
        when(organization.listRepositories()).thenReturn(pagedRepositories);
        when(pagedRepositories.toList()).thenReturn(List.of(publicRepo));
    }

    /** Builds a GitHubService with the given token, recording what it prints to standard error. */
    private CapturedConstruction constructWithToken(String token) throws IOException {
        ByteArrayOutputStream errorOutput = new ByteArrayOutputStream();
        PrintStream originalError = System.err;
        System.setErr(new PrintStream(errorOutput, true));
        try (MockedStatic<GitHub> gitHubStatic = mockStatic(GitHub.class);
             MockedConstruction<GitHubBuilder> builders = mockConstruction(GitHubBuilder.class,
                     (builder, context) -> {
                         when(builder.withOAuthToken(anyString())).thenReturn(builder);
                         when(builder.build()).thenReturn(authenticatedGithub);
                     })) {
            gitHubStatic.when(GitHub::connectAnonymously).thenReturn(github);
            GitHubService service = new GitHubService(token);
            return new CapturedConstruction(service, errorOutput.toString());
        } finally {
            System.setErr(originalError);
        }
    }

    private record CapturedConstruction(GitHubService service, String errorOutput) {
    }

    /**
     * Builds a GitHubService whose underlying client is the given mock, whichever
     * construction path (anonymous or GITHUB_TOKEN) the current environment takes.
     */
    private GitHubService newServiceBackedBy(GitHub client) throws IOException {
        try (MockedStatic<GitHub> gitHubStatic = mockStatic(GitHub.class);
             MockedConstruction<GitHubBuilder> builders = mockConstruction(GitHubBuilder.class,
                     (builder, context) -> {
                         when(builder.withOAuthToken(anyString())).thenReturn(builder);
                         when(builder.build()).thenReturn(client);
                     })) {
            gitHubStatic.when(GitHub::connectAnonymously).thenReturn(client);
            return new GitHubService();
        }
    }
}
