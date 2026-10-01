package com.github.backup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.PagedIterable;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GitHubServiceTest {

    @Mock
    private GitHub github;

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
        IOException userNotFound = new IOException("{\"message\":\"Not Found\",\"status\":\"404\"}");
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
