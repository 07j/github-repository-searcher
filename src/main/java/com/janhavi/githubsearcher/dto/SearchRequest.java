package com.janhavi.githubsearcher.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class SearchRequest {

    @NotBlank(message = "query must not be empty")
    private String query;

    private String language;

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
