package CF_DuelProject.CF_DuelProject.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import lombok.extern.slf4j.Slf4j;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@Slf4j
public class CodeforcesService {

    private final RestTemplate restTemplate = createRestTemplate();

    private static RestTemplate createRestTemplate() {
        // Never let a hung Codeforces connection block the match scheduler.
        org.springframework.http.client.SimpleClientHttpRequestFactory f = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        f.setConnectTimeout(5000);
        f.setReadTimeout(10000);
        return new RestTemplate(f);
    }

    /**
     * Verifies if a Codeforces handle exists using the official Codeforces API (user.info).
     * Returns user profile details if found, or throws ResponseStatusException if not found.
     */
    public Map<String, Object> verifyHandle(String handle) {
        if (handle == null || handle.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please enter a Codeforces handle.");
        }

        String trimmed = handle.trim();
        try {
            String url = "https://codeforces.com/api/user.info?handles=" + URLEncoder.encode(trimmed, StandardCharsets.UTF_8);
            Map response = restTemplate.getForObject(url, Map.class);

            if (response == null || !"OK".equalsIgnoreCase(String.valueOf(response.get("status")))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Codeforces account not found. Please enter a valid Codeforces handle.");
            }

            List<Map<String, Object>> result = (List<Map<String, Object>>) response.get("result");
            if (result == null || result.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Codeforces account not found. Please enter a valid Codeforces handle.");
            }

            Map<String, Object> cfUser = result.get(0);
            String officialHandle = (String) cfUser.get("handle");

            Map<String, Object> details = new HashMap<>();
            details.put("handle", officialHandle != null ? officialHandle : trimmed);
            details.put("rating", cfUser.get("rating"));
            details.put("maxRating", cfUser.get("maxRating"));
            details.put("rank", cfUser.get("rank"));
            details.put("maxRank", cfUser.get("maxRank"));
            details.put("avatar", cfUser.get("avatar") != null ? cfUser.get("avatar") : cfUser.get("titlePhoto"));
            details.put("titlePhoto", cfUser.get("titlePhoto"));

            return details;
        } catch (HttpStatusCodeException e) {
            log.warn("Codeforces API rejected handle '{}': status={}, response={}", trimmed, e.getStatusCode(), e.getResponseBodyAsString());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Codeforces account not found. Please enter a valid Codeforces handle.");
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (Exception e) {
            log.error("Failed to connect to Codeforces API for handle '{}': {}", trimmed, e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Unable to reach Codeforces API. Please try again.");
        }
    }

    private final Map<String, Integer> ratingCache = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Retrieves the current Codeforces rating for a user.
     * Returns 0 for unrated users, or null if Codeforces API is unreachable and no cached rating exists.
     */
    public Integer getUserRating(String handle) {
        if (handle == null || handle.trim().isEmpty()) return null;
        String key = handle.trim().toLowerCase();
        Integer cached = ratingCache.get(key);
        if (cached != null) return cached;

        try {
            Map<String, Object> details = verifyHandle(handle.trim());
            Object r = details.get("rating");
            if (r instanceof Number num) {
                int rating = num.intValue();
                ratingCache.put(key, rating);
                return rating;
            } else {
                // unrated on Codeforces
                ratingCache.put(key, 0);
                return 0;
            }
        } catch (Exception e) {
            log.warn("Could not retrieve rating for handle {}: {}", handle, e.getMessage());
            return null;
        }
    }

    public void setCachedRating(String handle, int rating) {
        if (handle != null) {
            ratingCache.put(handle.trim().toLowerCase(), rating);
        }
    }

    // ───────────────────────── solve detection ─────────────────────────

    /** Minimum gap between two calls to the Codeforces API (CF allows at most 1 request / 2 s). */
    static final long MIN_CALL_INTERVAL_MS = 2100;
    /** A handle's submission list is reused for this long instead of calling Codeforces again. */
    static final long CACHE_TTL_MS = 3000;
    /** How many recent submissions are inspected per handle. */
    static final int SUBMISSIONS_WINDOW = 50;

    /** A verified accepted submission. {@code submissionId} is Codeforces' own globally increasing id. */
    public record SolveInfo(Date time, long submissionId) {}

    /** Codeforces could not be reached / answered with an error. Distinct from "the user has not solved it". */
    public static class CodeforcesUnavailableException extends RuntimeException {
        public CodeforcesUnavailableException(String message, Throwable cause) { super(message, cause); }
    }

    private record CachedSubmissions(long fetchedAtMs, List<Map<String, Object>> submissions) {}

    private final Object throttleLock = new Object();
    private long lastCallAtMs = 0;
    private final Map<String, CachedSubmissions> submissionCache = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Returns the earliest accepted submission of {@code problemUrl} by {@code user} made at or after
     * {@code matchStartTime}, or null if there is none. Throws {@link CodeforcesUnavailableException} when the
     * answer is unknown, so callers never mistake an outage for "not solved".
     */
    public SolveInfo fetchSolve(String user, String problemUrl, Date matchStartTime) {
        String[] parts = extractProblem(problemUrl);
        int contestId = Integer.parseInt(parts[0]);
        String index = parts[1];

        SolveInfo earliest = null;
        for (Map<String, Object> sub : recentSubmissions(user)) {
            Object problemObj = sub.get("problem");
            if (!(problemObj instanceof Map<?, ?> problem)) continue;

            Object created = sub.get("creationTimeSeconds");
            if (!(created instanceof Number)) continue;
            Date submissionTime = new Date(((Number) created).longValue() * 1000);

            // submissions are newest-first: everything after this one is older than the match
            if (submissionTime.before(matchStartTime)) break;

            Object cid = problem.get("contestId");
            if (!(cid instanceof Number) || ((Number) cid).intValue() != contestId) continue;
            if (!index.equals(problem.get("index"))) continue;
            if (!"OK".equals(sub.get("verdict"))) continue;

            long id = sub.get("id") instanceof Number n ? n.longValue() : Long.MAX_VALUE;
            if (earliest == null
                    || submissionTime.before(earliest.time())
                    || (submissionTime.equals(earliest.time()) && id < earliest.submissionId())) {
                earliest = new SolveInfo(submissionTime, id);
            }
        }
        return earliest;
    }

    /** Backwards-compatible lookup: any failure is reported as "no solve". Prefer {@link #fetchSolve}. */
    public Date getSolveTime(String user, String problemUrl, Date matchStartTime) {
        try {
            SolveInfo s = fetchSolve(user, problemUrl, matchStartTime);
            return s == null ? null : s.time();
        } catch (Exception e) {
            log.error("CF API error for user: {}", user, e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> recentSubmissions(String user) {
        String key = user.toLowerCase();
        CachedSubmissions cached = submissionCache.get(key);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.fetchedAtMs() < CACHE_TTL_MS) {
            return cached.submissions();
        }

        throttle();
        try {
            String url = "https://codeforces.com/api/user.status?handle="
                    + URLEncoder.encode(user, StandardCharsets.UTF_8) + "&from=1&count=" + SUBMISSIONS_WINDOW;
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response == null || !"OK".equals(response.get("status"))
                    || !(response.get("result") instanceof List)) {
                throw new CodeforcesUnavailableException("Unexpected Codeforces response for " + user, null);
            }
            List<Map<String, Object>> result = (List<Map<String, Object>>) response.get("result");
            if (submissionCache.size() > 2000) submissionCache.clear(); // hard bound on memory
            submissionCache.put(key, new CachedSubmissions(System.currentTimeMillis(), result));
            return result;
        } catch (CodeforcesUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new CodeforcesUnavailableException("Codeforces API call failed for " + user + ": " + e.getMessage(), e);
        }
    }

    /** Serialises all Codeforces calls so the whole application stays under the public API rate limit. */
    private void throttle() {
        synchronized (throttleLock) {
            long wait = lastCallAtMs + MIN_CALL_INTERVAL_MS - System.currentTimeMillis();
            if (wait > 0) {
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new CodeforcesUnavailableException("Interrupted while waiting for Codeforces rate limit", ie);
                }
            }
            lastCallAtMs = System.currentTimeMillis();
        }
    }

    private String[] extractProblem(String url) {
        // Supports:
        // 1) https://codeforces.com/problemset/problem/1900/A
        // 2) https://codeforces.com/contest/2164/problem/A
        String[] parts = url.split("/");

        for (int i = 0; i < parts.length - 1; i++) {
            if ("problem".equals(parts[i])) {
                String index = parts[i + 1];

                // contest/<id>/problem/<index>
                if (i >= 2 && "contest".equals(parts[i - 2])) {
                    return new String[]{parts[i - 1], index};
                }

                // problemset/problem/<id>/<index>
                if (i >= 1 && "problemset".equals(parts[i - 1])) {
                    if (i + 2 < parts.length) {
                        return new String[]{parts[i + 1], parts[i + 2]};
                    }
                }

                // Generic fallback: treat previous segment as contestId
                if (i >= 1) {
                    return new String[]{parts[i - 1], index};
                }
            }
        }

        throw new IllegalArgumentException("Unsupported Codeforces problem URL: " + url);
    }
}