# URL Shortener

A backend-focused URL shortening service built with Spring Boot, designed to demonstrate real system-design trade-offs — caching strategy, concurrency-safe rate limiting, and measured performance — rather than just basic CRUD functionality.

## Features

- **Shorten & redirect** — converts long URLs into short, base62-encoded codes, with proper HTTP redirect semantics (302 + `Location` header)
- **Redis caching (cache-aside)** — repeat visits to a short link are served from Redis instead of Postgres, cutting response time from ~625ms to ~7ms (see [Performance](#performance) below)
- **Rate limiting** — Redis-backed fixed-window rate limiter (atomic `INCR`) protects the creation endpoint from abuse, returning `429 Too Many Requests` when exceeded
- **Clean error handling** — custom exceptions + a global `@RestControllerAdvice` handler return proper HTTP status codes (`404` for missing links, `400` with a clear message for invalid input) instead of raw stack traces
- **Input validation** — `@Valid` + Bean Validation rejects empty/missing URLs before they ever reach the database

## Tech Stack

- **Language / Framework:** Java, Spring Boot (Spring Web, Spring Data JPA)
- **Database:** PostgreSQL
- **Cache:** Redis
- **Load testing:** Apache JMeter

## Architecture

```
Client
  │
  ├── POST /api/shorten ──> Rate limiter (Redis) ──> UrlService ──> Postgres (save + base62 encode)
  │
  └── GET /{shortCode} ──> UrlService ──> Redis (cache-aside)
                                            │
                                            ├── HIT  → return cached URL
                                            └── MISS → Postgres lookup → backfill Redis (24h TTL) → return
```

## Performance

Load tested with Apache JMeter, 50 concurrent simulated users.

| Scenario | Average | Min | Max |
|---|---|---|---|
| Cache miss (Postgres lookup) | ~625ms | 609ms | 653ms |
| Cache hit (Redis, 50 concurrent) | 7ms | 4ms | 11ms |

**~90x reduction** in response time for cached (repeat) short-link visits under concurrent load.

*Methodology note: initial testing included JMeter's default "Follow Redirects" behavior, which measured the external destination site's load time rather than this service's own response time. Disabling it isolates the measurement to just this service's redirect logic.*

## Design Decisions

- **Cache-aside over write-through:** only caches links that are actually requested, rather than populating Redis for every created link (many of which may never be visited) — more memory-efficient at scale, at the cost of a slower first visit per link.
- **Fixed-window rate limiting:** chosen for simplicity and correctness under concurrency (Redis `INCR` is atomic, so concurrent requests can't race past the limit). Known trade-off: allows short bursts near window boundaries — a sliding window would fix this at the cost of added complexity.
- **Base62 encoding of the DB auto-increment ID:** guarantees uniqueness with no collision-check overhead, and produces short codes without needing a separate ID-generation service. Known limitation: sequential IDs are guessable — a production system would likely add randomization or a pre-allocated ID-range service.

## API

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/shorten` | Create a short URL. Body: `{"originalUrl": "https://..."}` |
| GET | `/{shortCode}` | Redirects to the original URL |

## Running Locally

**Prerequisites:** Java 17+, Maven, Docker (for Postgres and Redis)

```bash
# Start dependencies
docker run --name url-shortener-db -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=urlshortener -p 5432:5432 -d postgres:16
docker run --name url-shortener-redis -p 6379:6379 -d redis:7

# Run the app
./mvnw spring-boot:run
```

The app will be available at `http://localhost:8080`.
