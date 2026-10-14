package com.janhavi.githubsearcher.controller;

import com.janhavi.githubsearcher.dto.SearchRequest;
import com.janhavi.githubsearcher.model.GitHubRepo;
import com.janhavi.githubsearcher.service.GitHubService;
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

@RestController
@RequestMapping("/api/github")
public class RepoController {

    private final GitHubService gitHubService;

    public RepoController(GitHubService gitHubService) {
        this.gitHubService = gitHubService;
    }

    // POST /api/github/search
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

    // GET /api/github/repositories?language=Java&minStars=100&sort=stars
    @GetMapping("/repositories")
    public ResponseEntity<Map<String, Object>> getStoredRepositories(
            @RequestParam(required = false) String language,
            @RequestParam(required = false, defaultValue = "0") int minStars,
            @RequestParam(required = false, defaultValue = "stars") String sort) {

        List<GitHubRepo> repos = gitHubService.getStoredRepositories(language, minStars, sort);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("repositories", repos);
        return ResponseEntity.ok(response);
    }
}
