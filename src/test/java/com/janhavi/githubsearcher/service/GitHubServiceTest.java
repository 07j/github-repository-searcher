package com.janhavi.githubsearcher.service;

import com.janhavi.githubsearcher.exception.GitHubApiException;
import com.janhavi.githubsearcher.model.GitHubRepo;
import com.janhavi.githubsearcher.repository.RepoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GitHubServiceTest {

    @Mock
    private RepoRepository repoRepository;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private GitHubService gitHubService;

    @SuppressWarnings("unchecked")
    private void mockGitHubResponse(Map<String, Object> body) {
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(body));
    }

    @SuppressWarnings("unchecked")
    private void mockGitHubError(RuntimeException exception) {
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenThrow(exception);
    }

    @Test
    void searchMapsGitHubItemsAndSavesThem() {
        Map<String, Object> owner = new HashMap<>();
        owner.put("login", "user123");

        Map<String, Object> item = new HashMap<>();
        item.put("id", 123456);
        item.put("name", "spring-boot-example");
        item.put("description", "An example repository for Spring Boot");
        item.put("owner", owner);
        item.put("language", "Java");
        item.put("stargazers_count", 450);
        item.put("forks_count", 120);
        item.put("updated_at", "2024-01-01T12:00:00Z");

        Map<String, Object> body = new HashMap<>();
        body.put("items", List.of(item));
        mockGitHubResponse(body);

        List<GitHubRepo> result = gitHubService.searchAndSaveRepositories("spring boot", "Java", "stars");

        assertEquals(1, result.size());
        GitHubRepo repo = result.get(0);
        assertEquals(123456L, repo.getId());
        assertEquals("spring-boot-example", repo.getName());
        assertEquals("user123", repo.getOwner());
        assertEquals(450, repo.getStars());
        assertEquals(120, repo.getForks());
        assertEquals("2024-01-01T12:00:00Z", repo.getLastUpdated().toString());
        verify(repoRepository).saveAll(anyList());
    }

    @Test
    void searchWithNoResultsReturnsEmptyListAndSavesNothing() {
        Map<String, Object> body = new HashMap<>();
        body.put("items", Collections.emptyList());
        mockGitHubResponse(body);

        List<GitHubRepo> result = gitHubService.searchAndSaveRepositories("zzzz-no-such-repo", null, null);

        assertTrue(result.isEmpty());
        verify(repoRepository, never()).saveAll(anyList());
    }

    @Test
    void searchWhenGitHubRateLimitsThrows429() {
        mockGitHubError(new HttpClientErrorException(HttpStatus.FORBIDDEN));

        GitHubApiException ex = assertThrows(GitHubApiException.class,
                () -> gitHubService.searchAndSaveRepositories("spring", null, null));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatus());
    }

    @Test
    void searchWithInvalidQueryThrows400() {
        mockGitHubError(new HttpClientErrorException(HttpStatus.UNPROCESSABLE_ENTITY));

        GitHubApiException ex = assertThrows(GitHubApiException.class,
                () -> gitHubService.searchAndSaveRepositories("spring", "notalanguage", null));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void searchWhenGitHubIsUnreachableThrows503() {
        mockGitHubError(new ResourceAccessException("timeout"));

        GitHubApiException ex = assertThrows(GitHubApiException.class,
                () -> gitHubService.searchAndSaveRepositories("spring", null, null));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
    }

    @Test
    void searchWithMissingItemsThrows502() {
        mockGitHubResponse(new HashMap<>());

        GitHubApiException ex = assertThrows(GitHubApiException.class,
                () -> gitHubService.searchAndSaveRepositories("spring", null, null));

        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatus());
    }

    @Test
    void getStoredRepositoriesUsesLanguageFilterAndSort() {
        when(repoRepository.findByLanguageIgnoreCaseAndStarsGreaterThanEqual(
                eq("Java"), eq(100), eq(Sort.by(Sort.Direction.DESC, "forks"))))
                .thenReturn(Collections.emptyList());

        gitHubService.getStoredRepositories("Java", 100, "forks");

        verify(repoRepository).findByLanguageIgnoreCaseAndStarsGreaterThanEqual(
                "Java", 100, Sort.by(Sort.Direction.DESC, "forks"));
    }

    @Test
    void getStoredRepositoriesWithoutLanguageSortsByUpdatedDate() {
        when(repoRepository.findByStarsGreaterThanEqual(eq(0), eq(Sort.by(Sort.Direction.DESC, "lastUpdated"))))
                .thenReturn(Collections.emptyList());

        gitHubService.getStoredRepositories(null, 0, "updated");

        verify(repoRepository).findByStarsGreaterThanEqual(0, Sort.by(Sort.Direction.DESC, "lastUpdated"));
    }

    @Test
    void getStoredRepositoriesRejectsNegativeMinStars() {
        assertThrows(IllegalArgumentException.class,
                () -> gitHubService.getStoredRepositories(null, -5, "stars"));
    }
}
