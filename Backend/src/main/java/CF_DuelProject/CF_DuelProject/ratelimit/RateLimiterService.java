package CF_DuelProject.CF_DuelProject.ratelimit;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import io.github.bucket4j.*;

@Service
public class RateLimiterService {

    /** Upper bound on tracked clients; the least recently used bucket is evicted first. */
    static final int MAX_BUCKETS = 10_000;

    private final Map<String, Bucket> cache = Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Bucket> eldest) {
                    return size() > MAX_BUCKETS;
                }
            });

    public Bucket resolveBucket(String key, long capacity, long refillTokens, Duration duration) {
        return cache.computeIfAbsent(key, k -> {
            Bandwidth limit = Bandwidth.builder()
                    .capacity(capacity)
                    .refillGreedy(refillTokens, duration)
                    .build();
            return Bucket.builder()
                    .addLimit(limit)
                    .build();
        });
    }

    public int size() {
        return cache.size();
    }
}
