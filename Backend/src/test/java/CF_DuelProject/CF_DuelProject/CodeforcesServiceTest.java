package CF_DuelProject.CF_DuelProject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import CF_DuelProject.CF_DuelProject.service.CodeforcesService;

/** Codeforces client behaviour against a mocked HTTP layer — the real Codeforces API is NOT contacted. */
class CodeforcesServiceTest {

    private static final String PROBLEM = "https://codeforces.com/contest/1900/problem/A";
    private final Date start = new Date(1_000_000_000_000L);

    private RestTemplate http;
    private CodeforcesService cf;

    @BeforeEach
    void setUp() {
        http = mock(RestTemplate.class);
        cf = new CodeforcesService();
        ReflectionTestUtils.setField(cf, "restTemplate", http);
    }

    private Map<String, Object> sub(long id, long secondsAfterStart, int contest, String index, String verdict) {
        return Map.of("id", id, "creationTimeSeconds", start.getTime() / 1000 + secondsAfterStart,
                "verdict", verdict, "problem", Map.of("contestId", contest, "index", index));
    }

    private void respond(List<Map<String, Object>> newestFirst) {
        when(http.getForObject(anyString(), eq(Map.class))).thenReturn(Map.of("status", "OK", "result", newestFirst));
    }

    @Test
    void detectsEarliestAcceptedSubmissionOfTheRightProblemAfterTheStart() {
        respond(List.of(
                sub(9, 300, 1900, "A", "OK"),            // later accepted
                sub(8, 200, 1900, "A", "WRONG_ANSWER"),  // wrong verdict
                sub(7, 150, 1900, "B", "OK"),            // other problem
                sub(6, 100, 1900, "A", "OK"),            // <- earliest valid
                sub(5, -50, 1900, "A", "OK")));          // before the match: ignored
        CodeforcesService.SolveInfo s = cf.fetchSolve("alice", PROBLEM, start);
        assertEquals(6L, s.submissionId());
        assertEquals(start.getTime() + 100_000, s.time().getTime());
    }

    @Test
    void noAcceptedSubmission_isNullNotAnError() {
        respond(List.of(sub(1, 10, 1900, "A", "WRONG_ANSWER")));
        assertNull(cf.fetchSolve("alice", PROBLEM, start));
    }

    @Test
    void outageIsReportedAsUnavailableNeverAsNotSolved() {
        when(http.getForObject(anyString(), eq(Map.class))).thenThrow(new ResourceAccessException("timeout"));
        assertThrows(CodeforcesService.CodeforcesUnavailableException.class, () -> cf.fetchSolve("alice", PROBLEM, start));

        CodeforcesService cf2 = new CodeforcesService();
        RestTemplate http2 = mock(RestTemplate.class);
        ReflectionTestUtils.setField(cf2, "restTemplate", http2);
        when(http2.getForObject(anyString(), eq(Map.class))).thenReturn(Map.of("status", "FAILED", "comment", "Call limit exceeded"));
        assertThrows(CodeforcesService.CodeforcesUnavailableException.class, () -> cf2.fetchSolve("alice", PROBLEM, start));

        CodeforcesService cf3 = new CodeforcesService();
        RestTemplate http3 = mock(RestTemplate.class);
        ReflectionTestUtils.setField(cf3, "restTemplate", http3);
        when(http3.getForObject(anyString(), eq(Map.class))).thenReturn(null);
        assertThrows(CodeforcesService.CodeforcesUnavailableException.class, () -> cf3.fetchSolve("alice", PROBLEM, start));
    }

    @Test
    void legacyLookupStillReturnsNullOnFailure() {
        when(http.getForObject(anyString(), eq(Map.class))).thenThrow(new ResourceAccessException("down"));
        assertNull(cf.getSolveTime("alice", PROBLEM, start));
    }

    @Test
    void repeatedLookupsForTheSameHandleReuseTheCachedAnswer() {
        respond(List.of(sub(1, 10, 1900, "A", "OK")));
        cf.fetchSolve("Alice", PROBLEM, start);
        cf.fetchSolve("alice", PROBLEM, start);
        cf.fetchSolve("ALICE", "https://codeforces.com/contest/1900/problem/B", start);
        verify(http, times(1)).getForObject(anyString(), eq(Map.class));
    }

    @Test
    void callsToCodeforcesAreSpacedOutToRespectItsRateLimit() {
        respond(List.of());
        long t0 = System.currentTimeMillis();
        cf.fetchSolve("alice", PROBLEM, start);
        cf.fetchSolve("bob", PROBLEM, start);
        cf.fetchSolve("carol", PROBLEM, start);
        long elapsed = System.currentTimeMillis() - t0;
        // 3 distinct handles => 3 calls => at least 2 enforced gaps of ~2.1 s
        assertTrue(elapsed >= 2 * CodeforcesService_MIN_GAP - 200, "elapsed=" + elapsed);
    }

    private static final long CodeforcesService_MIN_GAP = 2100;

    @Test
    void handleIsUrlEncodedInTheRequest() {
        respond(List.of());
        cf.fetchSolve("we ird&handle", PROBLEM, start);
        org.mockito.ArgumentCaptor<String> url = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(http).getForObject(url.capture(), eq(Map.class));
        assertTrue(url.getValue().contains("handle=we+ird%26handle"), url.getValue());
    }
}
