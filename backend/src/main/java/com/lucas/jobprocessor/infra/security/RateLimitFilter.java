package com.lucas.jobprocessor.infra.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MS = 60_000;

    private final ObjectMapper objectMapper;
    private final int requestsPerMinute;
    private final boolean enabled;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong requestCount = new AtomicLong();

    public RateLimitFilter(
            ObjectMapper objectMapper,
            @Value("${app.rate-limit.requests-per-minute:100}") int requestsPerMinute,
            @Value("${app.rate-limit.enabled:true}") boolean enabled) {
        this.objectMapper = objectMapper;
        this.requestsPerMinute = requestsPerMinute;
        this.enabled = enabled;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !enabled
                || !path.startsWith("/api/")
                || path.startsWith("/actuator/")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs/")
                || path.equals("/v3/api-docs");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String clientIp = request.getRemoteAddr();
        long now = System.currentTimeMillis();
        cleanupExpiredWindows(now);
        Window window = windows.computeIfAbsent(clientIp, ignored -> new Window(now));

        if (window.tryAcquire(now, requestsPerMinute)) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Map.of(
                "error", "RATE_LIMIT_EXCEEDED",
                "message", "Rate limit exceeded. Try again in a moment.",
                "timestamp", OffsetDateTime.now().toString(),
                "path", request.getRequestURI()
        ));
    }

    private void cleanupExpiredWindows(long now) {
        if (requestCount.incrementAndGet() % 100 != 0) {
            return;
        }

        windows.entrySet().removeIf(entry -> now - entry.getValue().startedAt() >= WINDOW_MS * 2);
    }

    private static final class Window {
        private long startedAt;
        private int count;

        private Window(long startedAt) {
            this.startedAt = startedAt;
        }

        private synchronized boolean tryAcquire(long now, int limit) {
            if (now - startedAt >= WINDOW_MS) {
                startedAt = now;
                count = 0;
            }

            if (count >= limit) {
                return false;
            }

            count++;
            return true;
        }

        private synchronized long startedAt() {
            return startedAt;
        }
    }
}
