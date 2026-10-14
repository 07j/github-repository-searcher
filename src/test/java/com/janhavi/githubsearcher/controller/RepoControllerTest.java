package com.janhavi.githubsearcher.controller;

import com.janhavi.githubsearcher.exception.GitHubApiException;
import com.janhavi.githubsearcher.model.GitHubRepo;
import com.janhavi.githubsearcher.service.GitHubService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RepoController.class)
class RepoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GitHubService gitHubService;

    @Test
    void searchReturnsSavedRepositories() throws Exception {
        GitHubRepo repo = new GitHubRepo();
        repo.setId(456L);
        repo.setName("java-guide");

        when(gitHubService.searchAndSaveRepositories(anyString(), anyString(), anyString()))
                .thenReturn(Collections.singletonList(repo));

        String requestBody = "{\"query\": \"spring boot\", \"language\": \"Java\", \"sort\": \"stars\"}";

        mockMvc.perform(post("/api/github/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Repositories fetched and saved successfully"))
                .andExpect(jsonPath("$.repositories[0].id").value(456))
                .andExpect(jsonPath("$.repositories[0].name").value("java-guide"));
    }

    @Test
    void searchWithNoResultsReturnsEmptyList() throws Exception {
        when(gitHubService.searchAndSaveRepositories(anyString(), anyString(), anyString()))
                .thenReturn(Collections.emptyList());

        String requestBody = "{\"query\": \"zzzz-no-such-repo\", \"language\": \"Java\", \"sort\": \"stars\"}";

        mockMvc.perform(post("/api/github/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("No repositories found for the given search"))
                .andExpect(jsonPath("$.repositories").isEmpty());
    }

    @Test
    void searchWithEmptyQueryReturnsBadRequest() throws Exception {
        String requestBody = "{\"query\": \"\", \"language\": \"Java\"}";

        mockMvc.perform(post("/api/github/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("query must not be empty"));
    }

    @Test
    void searchWithInvalidSortReturnsBadRequest() throws Exception {
        String requestBody = "{\"query\": \"spring\", \"sort\": \"name\"}";

        mockMvc.perform(post("/api/github/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchWhenRateLimitedReturns429() throws Exception {
        when(gitHubService.searchAndSaveRepositories(anyString(), isNull(), isNull()))
                .thenThrow(new GitHubApiException(HttpStatus.TOO_MANY_REQUESTS,
                        "GitHub API rate limit exceeded. Please try again later."));

        mockMvc.perform(post("/api/github/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\": \"spring\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("GitHub API rate limit exceeded. Please try again later."));
    }

    @Test
    void getStoredRepositoriesWithFilters() throws Exception {
        GitHubRepo repo = new GitHubRepo();
        repo.setId(123L);
        repo.setName("spring-boot-example");
        repo.setLanguage("Java");
        repo.setStars(450);

        when(gitHubService.getStoredRepositories(eq("Java"), eq(100), eq("stars")))
                .thenReturn(Collections.singletonList(repo));

        mockMvc.perform(get("/api/github/repositories")
                        .param("language", "Java")
                        .param("minStars", "100")
                        .param("sort", "stars"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repositories").isArray())
                .andExpect(jsonPath("$.repositories[0].name").value("spring-boot-example"))
                .andExpect(jsonPath("$.repositories[0].stars").value(450));
    }

    @Test
    void getStoredRepositoriesWithInvalidSortReturnsBadRequest() throws Exception {
        when(gitHubService.getStoredRepositories(isNull(), anyInt(), eq("name")))
                .thenThrow(new IllegalArgumentException("sort must be one of: stars, forks, updated"));

        mockMvc.perform(get("/api/github/repositories").param("sort", "name"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("sort must be one of: stars, forks, updated"));
    }

    @Test
    void getStoredRepositoriesWithTextMinStarsReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/github/repositories").param("minStars", "abc"))
                .andExpect(status().isBadRequest());
    }
}
