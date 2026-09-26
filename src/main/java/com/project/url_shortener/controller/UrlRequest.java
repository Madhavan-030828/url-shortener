package com.project.url_shortener.controller;
import jakarta.validation.constraints.NotBlank;

public class UrlRequest {
    @NotBlank(message = "originalUrl must not be empty")
    private String originalUrl;

    public String getOriginalUrl() {
        return originalUrl;
    }

    public void setOriginalUrl(String originalUrl) {
        this.originalUrl = originalUrl;
    }
}
