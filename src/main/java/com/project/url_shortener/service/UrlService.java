package com.project.url_shortener.service;

import com.project.url_shortener.entity.Url;
import com.project.url_shortener.exception.UrlNotFoundException;
import com.project.url_shortener.repository.UrlRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class UrlService {
    @Autowired
    private UrlRepository urlRepository;

    private static final String BASE62 = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    public String createShortUrl(String originalUrl) {
        Url url = new Url();
        url.setOriginalUrl(originalUrl);
        url.setCreatedAt(LocalDateTime.now());

        // Save first (without shortUrl) to get the auto-generated ID
        Url savedUrl = urlRepository.save(url);

        // Now generate the short code using that ID
        String shortCode = encodeBase62(savedUrl.getId());
        savedUrl.setShortUrl(shortCode);

        // Save again to persist the short code
        urlRepository.save(savedUrl);

        return shortCode;
    }

    private String encodeBase62(int id) {
        StringBuilder sb = new StringBuilder();
        while (id > 0) {
            sb.append(BASE62.charAt(id % 62));
            id = id / 62;
        }
        return sb.reverse().toString();
    }

    public String getOriginalUrl(String shortCode) {

        // 1. Check Redis first
        String cachedUrl = redisTemplate.opsForValue().get(shortCode);
        if (cachedUrl != null) {
            return cachedUrl;
        }

        // 2. Cache miss — fall back to Postgres
        String originalUrl = urlRepository.findByShortUrl(shortCode)
                .map(Url::getOriginalUrl)
                .orElseThrow(() -> new UrlNotFoundException("No URL found for short code: " + shortCode));

        // 3. Populate the cache for next time
        redisTemplate.opsForValue().set(shortCode, originalUrl, Duration.ofHours(24));

        return originalUrl;
    }

    private static final int RATE_LIMIT = 5; // max requests per window
    private static final int WINDOW_SECONDS = 60;

    public boolean isRateLimited(String clientIp) {
        String windowKey = "ratelimit:" + clientIp + ":" + (System.currentTimeMillis() / 1000 / WINDOW_SECONDS);

        Long count = redisTemplate.opsForValue().increment(windowKey);

        if (count != null && count == 1) {
            // first request in this window — set expiry so it cleans up automatically
            redisTemplate.expire(windowKey, Duration.ofSeconds(WINDOW_SECONDS));
        }

        return count != null && count > RATE_LIMIT;
    }
}