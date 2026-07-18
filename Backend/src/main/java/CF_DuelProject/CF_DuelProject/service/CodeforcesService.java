package CF_DuelProject.CF_DuelProject.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Service
@Slf4j
public class CodeforcesService {

    private final RestTemplate restTemplate = new RestTemplate();

  public Date getSolveTime(String user, String problemUrl, Date matchStartTime) {

    try {
        // get only 10
        String url = "https://codeforces.com/api/user.status?handle=" + user+"&count=10";

        Map response = restTemplate.getForObject(url, Map.class);

        log.debug("CF response for {}: {}", user, response.get("status"));

        List<Map> submissions = (List<Map>)response.get("result");
        if (submissions == null) return null;

        // ✅ extract contestId + index from URL
        String[] parts = extractProblem(problemUrl);
        int contestId = Integer.parseInt(parts[0]);
        String index = parts[1];

        log.debug("TARGET → {} {}", contestId, index);

        Date earliest = null;

        // ✅ Optimize: limit to 10 most recent submissions
        int maxSubmissions = Math.min(10, submissions.size());

        for (int i = 0; i < maxSubmissions; i++) {
            Map sub = submissions.get(i);

            Map problem = (Map) sub.get("problem");
            if (problem == null) continue;

            // ✅ Check time early - break if before match start (stops checking older submissions)
            long creationTime = ((Number) sub.get("creationTimeSeconds")).longValue();
            Date submissionTime = new Date(creationTime * 1000);

            if (submissionTime.before(matchStartTime)) {
                break; // No need to check older submissions
            }

            int contestIdFromAPI = ((Number) problem.get("contestId")).intValue();
            String indexFromAPI = (String) problem.get("index");

            // 🔥 DEBUG
            log.debug("API → {} {}", contestIdFromAPI, indexFromAPI);

            if (contestIdFromAPI != contestId) continue;
            if (!index.equals(indexFromAPI)) continue;

            Object verdict = sub.get("verdict");
            if (verdict == null || !verdict.equals("OK")) continue;

            log.debug("MATCH FOUND → {}", submissionTime);

            if (earliest == null || submissionTime.before(earliest)) {
                earliest = submissionTime;
            }
        }

        return earliest;

    } catch (Exception e) {
        log.error("CF API error for user: {}", user, e);
        return null;
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