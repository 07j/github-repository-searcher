package com.janhavi.githubsearcher.exception;

import org.springframework.http.HttpStatus;

// Thrown when the GitHub API call fails. Carries the HTTP status we want to send back to the client.
public class GitHubApiException extends RuntimeException {

    private final HttpStatus status;

    public GitHubApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
