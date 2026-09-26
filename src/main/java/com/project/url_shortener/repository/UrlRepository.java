package com.project.url_shortener.repository;

import com.project.url_shortener.entity.Url;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UrlRepository extends JpaRepository<Url, Integer> {
    Optional<Url> findByShortUrl(String shortUrl);
}