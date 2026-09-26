package com.project.url_shortener.controller;

import com.project.url_shortener.service.UrlService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class UrlController {
    @Autowired
    private UrlService urlService;

    @PostMapping("/api/shorten")
    public ResponseEntity<String> createShortUrl(@Valid @RequestBody UrlRequest request, HttpServletRequest httpRequest) {
        String clientIp = httpRequest.getRemoteAddr();

        if (urlService.isRateLimited(clientIp)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body("Rate limit exceeded. Try again later.");
        }

        String shortCode = urlService.createShortUrl(request.getOriginalUrl());
        String shortUrl = "http://localhost:8080/" + shortCode;
        return ResponseEntity.ok(shortUrl);
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode) {
        String originalUrl = urlService.getOriginalUrl(shortCode);
        return ResponseEntity.status(302)
                .header("Location", originalUrl)
                .build();
    }
}