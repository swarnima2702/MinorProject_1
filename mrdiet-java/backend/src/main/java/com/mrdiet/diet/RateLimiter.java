package com.mrdiet.diet;

import com.mrdiet.common.ApiException;
import com.mrdiet.config.AppProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Simple per-user limit on AI calls so nobody can run up your OpenAI bill. */
@Component
public class RateLimiter {

    private final int maxPerHour;
    private final Map<Long, Deque<Instant>> calls = new ConcurrentHashMap<>();

    public RateLimiter(AppProperties props) {
        this.maxPerHour = props.ai() == null || props.ai().maxGenerationsPerHour() <= 0
                ? 30 : props.ai().maxGenerationsPerHour();
    }

    public void check(long userId) {
        Instant cutoff = Instant.now().minus(Duration.ofHours(1));
        Deque<Instant> q = calls.computeIfAbsent(userId, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && q.peekFirst().isBefore(cutoff)) q.pollFirst();
            if (q.size() >= maxPerHour) {
                throw ApiException.tooMany("Too many AI requests. Please try again in a little while.");
            }
            q.addLast(Instant.now());
        }
    }
}
