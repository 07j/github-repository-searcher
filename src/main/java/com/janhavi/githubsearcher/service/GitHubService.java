package com.janhavi.githubsearcher.service;

import com.janhavi.githubsearcher.exception.GitHubApiException;
import com.janhavi.githubsearcher.model.GitHubRepo;
import com.janhavi.githubsearcher.repository.RepoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class GitHubService {

    private static final String GITHUB_SEARCH_URL = "https://api.github.com/search/repositories";

    private final RepoRepository repoRepository;
    private final RestTemplate restTemplate;

    @Value("${github.api.token:}")
    private String githubToken;

    public GitHubService(RepoRepository repoRepository, RestTemplate restTemplate) {
        this.repoRepository = repoRepository;
        this.restTemplate = restTemplate;
    }

    // ---------- 1. Search GitHub and save the results ----------

    public List<GitHubRepo> searchAndSaveRepositories(String query, String language, String sort) {
        URI uri = buildSearchUri(query, language, sort);
        Map<String, Object> responseBody = callGitHub(uri);

        Object itemsObject = responseBody.get("items");
        if (!(itemsObject instanceof List<?> items)) {
            throw new GitHubApiException(HttpStatus.BAD_GATEWAY, "Invalid response from GitHub API: 'items' is missing");
        }

        List<GitHubRepo> repos = new ArrayList<>();
        for (Object itemObject : items) {
            if (itemObject instanceof Map<?, ?> item) {
                GitHubRepo repo = mapToRepo(item);
                if (repo != null) {
                    repos.add(repo);
                }
            }
        }

        // Empty search result: nothing to save
        if (repos.isEmpty()) {
            return repos;
        }

        // The GitHub ID is the primary key, so save() updates existing rows instead of duplicating them
        repoRepository.saveAll(repos);
        return repos;
    }

    private URI buildSearchUri(String query, String language, String sort) {
        // "in:name" makes GitHub match the text against the repository name (partial or full)
        String searchText = query.trim() + " in:name";
        if (language != null && !language.isBlank()) {
            searchText = searchText + " language:" + language.trim();
        }

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(GITHUB_SEARCH_URL)
                .queryParam("q", "{q}")
                .queryParam("per_page", 30);

        if (sort != null && !sort.isBlank()) {
            builder.queryParam("sort", sort).queryParam("order", "desc");
        }

        // encode() + buildAndExpand() fully encodes the search text, including spaces and "+" (e.g. C++)
        return builder.encode().buildAndExpand(searchText).toUri();
    }

    private Map<String, Object> callGitHub(URI uri) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.ACCEPT, "application/vnd.github+json");
        headers.set(HttpHeaders.USER_AGENT, "github-repository-searcher");
        if (githubToken != null && !githubToken.isBlank()) {
            headers.setBearerAuth(githubToken);
        }

        ResponseEntity<Map<String, Object>> response;
        try {
            response = restTemplate.exchange(
                    uri,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
        } catch (HttpStatusCodeException e) {
            int statusCode = e.getStatusCode().value();
            if (statusCode == 403 || statusCode == 429) {
                throw new GitHubApiException(HttpStatus.TOO_MANY_REQUESTS,
                        "GitHub API rate limit exceeded. Please try again later.");
            }
            if (statusCode == 422) {
                throw new GitHubApiException(HttpStatus.BAD_REQUEST,
                        "GitHub could not process the search query. Check the query and language values.");
            }
            throw new GitHubApiException(HttpStatus.BAD_GATEWAY,
                    "GitHub API returned an error with status " + statusCode);
        } catch (ResourceAccessException e) {
            throw new GitHubApiException(HttpStatus.SERVICE_UNAVAILABLE, "Could not connect to GitHub API");
        } catch (RestClientException e) {
            throw new GitHubApiException(HttpStatus.BAD_GATEWAY, "Invalid response from GitHub API");
        }

        Map<String, Object> body = response.getBody();
        if (body == null) {
            throw new GitHubApiException(HttpStatus.BAD_GATEWAY, "Empty response from GitHub API");
        }
        return body;
    }

    private GitHubRepo mapToRepo(Map<?, ?> item) {
        Object idObject = item.get("id");
        Object nameObject = item.get("name");

        // Skip broken entries without an ID or name
        if (!(idObject instanceof Number) || !(nameObject instanceof String)) {
            return null;
        }

        GitHubRepo repo = new GitHubRepo();
        repo.setId(((Number) idObject).longValue());
        repo.setName((String) nameObject);
        repo.setDescription(getString(item, "description"));
        repo.setLanguage(getString(item, "language"));
        repo.setStars(getInt(item, "stargazers_count"));
        repo.setForks(getInt(item, "forks_count"));

        Object ownerObject = item.get("owner");
        if (ownerObject instanceof Map<?, ?> owner) {
            repo.setOwner(getString(owner, "login"));
        }

        String updatedAt = getString(item, "updated_at");
        if (updatedAt != null) {
            try {
                repo.setLastUpdated(Instant.parse(updatedAt));
            } catch (DateTimeParseException e) {
                repo.setLastUpdated(null);
            }
        }
        return repo;
    }

    private String getString(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value instanceof String ? (String) value : null;
    }

    private int getInt(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    // ---------- 2. Read stored results from PostgreSQL ----------

    public List<GitHubRepo> getStoredRepositories(String language, int minStars, String sort) {
        if (minStars < 0) {
            throw new IllegalArgumentException("minStars must be 0 or more");
        }

        String sortField;
        if (sort == null || sort.isBlank() || sort.equals("stars")) {
            sortField = "stars";
        } else if (sort.equals("forks")) {
            sortField = "forks";
        } else if (sort.equals("updated")) {
            sortField = "lastUpdated";
        } else {
            throw new IllegalArgumentException("sort must be one of: stars, forks, updated");
        }
        Sort sorting = Sort.by(Sort.Direction.DESC, sortField);

        if (language != null && !language.isBlank()) {
            return repoRepository.findByLanguageIgnoreCaseAndStarsGreaterThanEqual(language.trim(), minStars, sorting);
        }
        return repoRepository.findByStarsGreaterThanEqual(minStars, sorting);
    }
}
