package com.janhavi.githubsearcher.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class SearchRequest {

    @Schema(description = "Text to match against repository names", example = "spring boot")
    @NotBlank(message = "query must not be empty")
    private String query;

    @Schema(description = "Programming language (optional)", example = "Java")
    private String language;

    @Schema(description = "stars, forks or updated (optional)", example = "stars")
    // Optional. Null is allowed; if given it must be one of these three values.
    @Pattern(regexp = "stars|forks|updated", message = "sort must be one of: stars, forks, updated")
    private String sort;

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public String getSort() { return sort; }
    public void setSort(String sort) { this.sort = sort; }
}
