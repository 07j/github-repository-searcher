package com.janhavi.githubsearcher.controller;

import com.janhavi.githubsearcher.dto.SearchRequest;
import com.janhavi.githubsearcher.model.GitHubRepo;
import com.janhavi.githubsearcher.service.GitHubService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "GitHub Repositories", description = "Search GitHub and query saved repositories")
@RestController
@RequestMapping("/api/github")
public class RepoController {

    private final GitHubService gitHubService;

    public RepoController(GitHubService gitHubService) {
        this.gitHubService = gitHubService;
    }

    @Operation(summary = "Search GitHub repositories and save the results",
            description = "Searches repository names on GitHub, then saves or updates each result in PostgreSQL.")
    @PostMapping("/search")
    public ResponseEntity<Map<String, Object>> searchAndSave(@Valid @RequestBody SearchRequest request) {
        List<GitHubRepo> repos = gitHubService.searchAndSaveRepositories(
                request.getQuery(),
                request.getLanguage(),
                request.getSort()
        );

        Map<String, Object> response = new LinkedHashMap<>();
        if (repos.isEmpty()) {
            response.put("message", "No repositories found for the given search");
        } else {
            response.put("message", "Repositories fetched and saved successfully");
        }
        response.put("repositories", repos);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get saved repositories with optional filters",
            description = "Filters by language and minimum stars, sorted by stars, forks or updated date (descending).")
    @GetMapping("/repositories")
    public ResponseEntity<Map<String, Object>> getStoredRepositories(
            @Parameter(description = "Programming language, e.g. Java", example = "Java")
            @RequestParam(required = false) String language,
            @Parameter(description = "Minimum star count", example = "100")
            @RequestParam(required = false, defaultValue = "0") int minStars,
            @Parameter(description = "stars, forks or updated", example = "stars")
            @RequestParam(required = false, defaultValue = "stars") String sort) {

        List<GitHubRepo> repos = gitHubService.getStoredRepositories(language, minStars, sort);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("repositories", repos);
        return ResponseEntity.ok(response);
    }
}
