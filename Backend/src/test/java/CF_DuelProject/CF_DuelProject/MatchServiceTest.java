package CF_DuelProject.CF_DuelProject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.mongodb.client.result.UpdateResult;

import CF_DuelProject.CF_DuelProject.model.MatchPrimary;
import CF_DuelProject.CF_DuelProject.model.MatchSecondary;
import CF_DuelProject.CF_DuelProject.model.User;
import CF_DuelProject.CF_DuelProject.repository.PrimaryMatchRepository;
import CF_DuelProject.CF_DuelProject.repository.SecondaryMatchRepository;
import CF_DuelProject.CF_DuelProject.repository.TournamentRepository;
import CF_DuelProject.CF_DuelProject.repository.UserRepository;
import CF_DuelProject.CF_DuelProject.service.CodeforcesService;
import CF_DuelProject.CF_DuelProject.service.MatchService;
import CF_DuelProject.CF_DuelProject.service.ProblemService;
import CF_DuelProject.CF_DuelProject.service.TournamentService;

/**
 * Pure unit tests (no Spring context, no MongoDB): they verify the service's decision logic around the
 * atomic Mongo operations it issues. Real-database atomicity is Mongo's guarantee and is NOT exercised here.
 */
class MatchServiceTest {

    private PrimaryMatchRepository primaryRepo;
    private SecondaryMatchRepository secondaryRepo;
    private UserRepository userRepo;
    private TournamentRepository tournamentRepo;
    private MongoTemplate mongo;
    private CodeforcesService cf;
    private ProblemService problems;
    private SimpMessagingTemplate ws;
    private TournamentService tournaments;
    private MatchService service;

    @BeforeEach
    void setUp() {
        primaryRepo = mock(PrimaryMatchRepository.class);
        secondaryRepo = mock(SecondaryMatchRepository.class);
        userRepo = mock(UserRepository.class);
        tournamentRepo = mock(TournamentRepository.class);
        mongo = mock(MongoTemplate.class);
        cf = mock(CodeforcesService.class);
        problems = mock(ProblemService.class);
        ws = mock(SimpMessagingTemplate.class);
        tournaments = mock(TournamentService.class);
        service = new MatchService(primaryRepo, secondaryRepo, userRepo, tournamentRepo, mongo, cf, problems, ws, tournaments);
    }

    // ───────────────────────── helpers ─────────────────────────

    private User verifiedUser(String handle, String email) {
        User u = new User();
        u.setId("id-" + handle);
        u.setEmail(email);
        u.setCfHandle(handle);
        u.setCodeforcesVerified(true);
        when(userRepo.findByCodeforcesHandleIgnoreCase(handle)).thenReturn(Optional.of(u));
        when(userRepo.findByCfHandleIgnoreCase(handle)).thenReturn(Optional.of(u));
        when(userRepo.findByEmail(email)).thenReturn(Optional.of(u));
        return u;
    }

    private MatchSecondary lobby(String status, String user2) {
        MatchSecondary m = new MatchSecondary();
        m.setId("lobby1");
        m.setInviteCode("ABC123");
        m.setUser1("alice");
        m.setPlayer1CFHandle("alice");
        m.setUser2(user2);
        m.setPlayer2CFHandle(user2);
        m.setStatus(status);
        m.setDurationMinutes(30);
        m.setDifficulty("EASY");
        return m;
    }

    private MatchPrimary live(Date start, Date end) {
        MatchPrimary m = new MatchPrimary();
        m.setId("m1");
        m.setInviteCode("ABC123");
        m.setUser1("alice");
        m.setUser2("bob");
        m.setPlayer1CFHandle("alice");
        m.setPlayer2CFHandle("bob");
        m.setStatus("ONGOING");
        m.setProblems(List.of("https://codeforces.com/contest/1/problem/A", "https://codeforces.com/contest/2/problem/A"));
        m.setStartTime(start);
        m.setEndTime(end);
        return m;
    }

    private static CodeforcesService.SolveInfo si(Date d) {
        return new CodeforcesService.SolveInfo(d, d.getTime());
    }

    private static Date in(long millis) {
        return new Date(System.currentTimeMillis() + millis);
    }

    private static String render(Query q) {
        return q.getQueryObject().toJson();
    }

    // ───────────────────────── join ─────────────────────────

    @Test
    void joinMatch_claimsSeatAtomicallyOnlyWhileWaitingAndEmpty() {
        verifiedUser("bob", "bob@x.com");
        when(secondaryRepo.findByInviteCode("ABC123")).thenReturn(Optional.of(lobby("WAITING", null)));
        MatchSecondary joined = lobby("READY", "bob");
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(MatchSecondary.class)))
                .thenReturn(joined);

        assertSame(joined, service.joinMatch("bob", " ABC123 "));

        ArgumentCaptor<Query> q = ArgumentCaptor.forClass(Query.class);
        verify(mongo).findAndModify(q.capture(), any(Update.class), any(FindAndModifyOptions.class), eq(MatchSecondary.class));
        String json = render(q.getValue());
        assertTrue(json.contains("WAITING"), json);
        assertTrue(json.contains("user2"), json);
        // never a blind whole-document save
        verify(secondaryRepo, never()).save(any());
    }

    @Test
    void joinMatch_losingTheRace_isConflictNotOverwrite() {
        verifiedUser("bob", "bob@x.com");
        when(secondaryRepo.findByInviteCode("ABC123")).thenReturn(Optional.of(lobby("WAITING", null)));
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(MatchSecondary.class)))
                .thenReturn(null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.joinMatch("bob", "ABC123"));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void joinMatch_cannotJoinOwnMatchFullMatchOrStoppedLobby() {
        verifiedUser("alice", "alice@x.com");
        verifiedUser("bob", "bob@x.com");

        when(secondaryRepo.findByInviteCode("ABC123")).thenReturn(Optional.of(lobby("WAITING", null)));
        assertEquals(HttpStatus.BAD_REQUEST,
                assertThrows(ResponseStatusException.class, () -> service.joinMatch("alice", "ABC123")).getStatusCode());

        when(secondaryRepo.findByInviteCode("ABC123")).thenReturn(Optional.of(lobby("READY", "carol")));
        assertEquals(HttpStatus.BAD_REQUEST,
                assertThrows(ResponseStatusException.class, () -> service.joinMatch("bob", "ABC123")).getStatusCode());

        // a lobby the creator already cancelled (FINISHED, no second player) must not be re-opened by a join
        when(secondaryRepo.findByInviteCode("ABC123")).thenReturn(Optional.of(lobby("FINISHED", null)));
        assertEquals(HttpStatus.BAD_REQUEST,
                assertThrows(ResponseStatusException.class, () -> service.joinMatch("bob", "ABC123")).getStatusCode());
        verify(mongo, never()).findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(MatchSecondary.class));
    }

    @Test
    void joinMatch_unverifiedPlayerRejected() {
        User u = new User();
        u.setEmail("eve@x.com");
        u.setCfHandle("eve");
        u.setCodeforcesVerified(false);
        when(userRepo.findByCodeforcesHandleIgnoreCase("eve")).thenReturn(Optional.of(u));

        assertEquals(HttpStatus.FORBIDDEN,
                assertThrows(ResponseStatusException.class, () -> service.joinMatch("eve", "ABC123")).getStatusCode());
    }

    /** Models Mongo's single-document atomicity with a compare-and-set and fires many simultaneous joins. */
    @Test
    void joinMatch_manyConcurrentJoiners_exactlyOneWins() throws Exception {
        int joiners = 20;
        for (int i = 0; i < joiners; i++) verifiedUser("player" + i, "p" + i + "@x.com");
        when(secondaryRepo.findByInviteCode("ABC123")).thenReturn(Optional.of(lobby("WAITING", null)));

        AtomicBoolean seatTaken = new AtomicBoolean(false);
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(MatchSecondary.class)))
                .thenAnswer(inv -> seatTaken.compareAndSet(false, true) ? lobby("READY", "someone") : null);

        AtomicInteger ok = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();
        runConcurrently(joiners, i -> {
            try {
                service.joinMatch("player" + i, "ABC123");
                ok.incrementAndGet();
            } catch (ResponseStatusException e) {
                if (e.getStatusCode() == HttpStatus.CONFLICT) conflict.incrementAndGet();
            }
        });

        assertEquals(1, ok.get());
        assertEquals(joiners - 1, conflict.get());
    }

    // ───────────────────────── start ─────────────────────────

    private void arrangeStartable() {
        verifiedUser("alice", "alice@x.com");
        verifiedUser("bob", "bob@x.com");
        when(secondaryRepo.findByInviteCode("ABC123")).thenReturn(Optional.of(lobby("READY", "bob")));
        when(problems.getMatchProblems(anyString(), anyString(), any()))
                .thenReturn(List.of("https://codeforces.com/contest/1/problem/A"));
        when(primaryRepo.save(any(MatchPrimary.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void startMatch_onlyCreatorMayStart() {
        arrangeStartable();
        assertEquals(HttpStatus.FORBIDDEN,
                assertThrows(ResponseStatusException.class, () -> service.startMatch("bob", "ABC123")).getStatusCode());
        verify(primaryRepo, never()).save(any());
    }

    @Test
    void startMatch_createsOneOngoingMatchWithServerSideDeadline() {
        arrangeStartable();
        when(mongo.findAndRemove(any(Query.class), eq(MatchSecondary.class))).thenReturn(lobby("READY", "bob"));

        long before = System.currentTimeMillis();
        MatchPrimary started = service.startMatch("alice", "ABC123");

        assertEquals("ONGOING", started.getStatus());
        assertEquals("alice", started.getPlayer1CFHandle());
        assertEquals("bob", started.getPlayer2CFHandle());
        assertEquals(1, started.getProblems().size());
        assertEquals(30 * 60 * 1000L, started.getEndTime().getTime() - started.getStartTime().getTime());
        assertTrue(started.getStartTime().getTime() >= before);
    }

    @Test
    void startMatch_secondStartLosesTheClaim_conflict() {
        arrangeStartable();
        when(mongo.findAndRemove(any(Query.class), eq(MatchSecondary.class))).thenReturn(null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.startMatch("alice", "ABC123"));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(primaryRepo, never()).save(any());
    }

    @Test
    void startMatch_noProblemsAvailable_failsInsteadOfStartingAnInstantlyFinishedDraw() {
        arrangeStartable();
        when(problems.getMatchProblems(anyString(), anyString(), any())).thenReturn(new ArrayList<>());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.startMatch("alice", "ABC123"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
        // lobby untouched so the creator can retry
        verify(mongo, never()).findAndRemove(any(Query.class), eq(MatchSecondary.class));
        verify(primaryRepo, never()).save(any());
    }

    @Test
    void startMatch_problemServiceThrows_lobbyUntouched() {
        arrangeStartable();
        when(problems.getMatchProblems(anyString(), anyString(), any())).thenThrow(new RuntimeException("CF down"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
                assertThrows(ResponseStatusException.class, () -> service.startMatch("alice", "ABC123")).getStatusCode());
        verify(mongo, never()).findAndRemove(any(Query.class), eq(MatchSecondary.class));
    }

    @Test
    void startMatch_duplicatePrimary_isConflictNot500() {
        arrangeStartable();
        when(mongo.findAndRemove(any(Query.class), eq(MatchSecondary.class))).thenReturn(lobby("READY", "bob"));
        when(primaryRepo.save(any(MatchPrimary.class))).thenThrow(new DuplicateKeyException("dup"));

        assertEquals(HttpStatus.CONFLICT,
                assertThrows(ResponseStatusException.class, () -> service.startMatch("alice", "ABC123")).getStatusCode());
    }

    @Test
    void startMatch_manyConcurrentStarts_exactlyOneMatchIsCreated() throws Exception {
        arrangeStartable();
        AtomicBoolean claimed = new AtomicBoolean(false);
        when(mongo.findAndRemove(any(Query.class), eq(MatchSecondary.class)))
                .thenAnswer(inv -> claimed.compareAndSet(false, true) ? lobby("READY", "bob") : null);

        AtomicInteger started = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        runConcurrently(10, i -> {
            try {
                service.startMatch("alice", "ABC123");
                started.incrementAndGet();
            } catch (ResponseStatusException e) {
                if (e.getStatusCode() == HttpStatus.CONFLICT) conflicts.incrementAndGet();
            }
        });

        assertEquals(1, started.get());
        assertEquals(9, conflicts.get());
        verify(primaryRepo, times(1)).save(any(MatchPrimary.class));
    }

    // ───────────────────────── solve / deadline ─────────────────────────

    private void arrangeScoreUpdate() {
        when(mongo.updateFirst(any(Query.class), any(Update.class), eq(MatchPrimary.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));
        when(primaryRepo.findById("m1")).thenReturn(Optional.of(new MatchPrimary()));
    }

    @Test
    void processMatch_solveAfterDeadlineIsNotCounted() {
        MatchPrimary m = live(in(-60_000), in(-5_000)); // already over
        Date late = in(-1_000);                          // solved AFTER the deadline
        when(cf.fetchSolve(eq("alice"), anyString(), any())).thenReturn(si(late));
        when(cf.fetchSolve(eq("bob"), anyString(), any())).thenReturn(null);
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(MatchPrimary.class)))
                .thenReturn(null);
        when(primaryRepo.findById("m1")).thenReturn(Optional.empty());

        service.processMatch(m);

        verify(mongo, never()).updateFirst(any(Query.class), any(Update.class), eq(MatchPrimary.class));
    }

    @Test
    void processMatch_solveJustBeforeDeadlineIsStillCreditedWhenSchedulerIsLate() {
        MatchPrimary m = live(in(-60_000), in(-2_000)); // deadline passed 2s ago, scheduler only now noticed
        when(cf.fetchSolve(eq("alice"), anyString(), any())).thenReturn(si(in(-10_000))); // solved 8s before the deadline
        when(cf.fetchSolve(eq("bob"), anyString(), any())).thenReturn(null);
        arrangeScoreUpdate();
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(MatchPrimary.class)))
                .thenReturn(null);

        service.processMatch(m);

        ArgumentCaptor<Update> upd = ArgumentCaptor.forClass(Update.class);
        verify(mongo).updateFirst(any(Query.class), upd.capture(), eq(MatchPrimary.class));
        assertTrue(upd.getValue().getUpdateObject().toJson().contains("score1"));
    }

    @Test
    void processMatch_earlierSolverWinsWhenBothSolved() {
        MatchPrimary m = live(in(-60_000), in(600_000));
        when(cf.fetchSolve(eq("alice"), anyString(), any())).thenReturn(si(in(-5_000)));
        when(cf.fetchSolve(eq("bob"), anyString(), any())).thenReturn(si(in(-9_000)));
        arrangeScoreUpdate();

        service.processMatch(m);

        ArgumentCaptor<Update> upd = ArgumentCaptor.forClass(Update.class);
        verify(mongo).updateFirst(any(Query.class), upd.capture(), eq(MatchPrimary.class));
        String json = upd.getValue().getUpdateObject().toJson();
        assertTrue(json.contains("score2"), json);
        assertFalse(json.contains("score1"), json);
    }

    @Test
    void processMatch_scoringOnlyAppliesToOngoingMatchAtExpectedProblemAndResetsSkipVotes() {
        MatchPrimary m = live(in(-60_000), in(600_000));
        when(cf.fetchSolve(eq("alice"), anyString(), any())).thenReturn(si(in(-5_000)));
        when(cf.fetchSolve(eq("bob"), anyString(), any())).thenReturn(null);
        arrangeScoreUpdate();

        service.processMatch(m);

        ArgumentCaptor<Query> q = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> u = ArgumentCaptor.forClass(Update.class);
        verify(mongo).updateFirst(q.capture(), u.capture(), eq(MatchPrimary.class));
        String query = render(q.getValue());
        assertTrue(query.contains("ONGOING"), query);
        assertTrue(query.contains("curIdx"), query);
        String update = u.getValue().getUpdateObject().toJson();
        assertTrue(update.contains("skipVoteUser1") && update.contains("skipVoteUser2"), update);
    }

    @Test
    void processMatch_noFinishWhileTimeRemainsAndNobodySolved() {
        MatchPrimary m = live(in(-60_000), in(600_000));
        when(cf.fetchSolve(anyString(), anyString(), any())).thenReturn(null);

        service.processMatch(m);

        verify(mongo, never()).updateFirst(any(Query.class), any(Update.class), eq(MatchPrimary.class));
        verify(mongo, never()).findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(MatchPrimary.class));
    }

    // ───────────────────────── finishing / duplicate results ─────────────────────────

    private void arrangeFreeze(MatchPrimary frozen) {
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(MatchPrimary.class)))
                .thenReturn(frozen);
    }

    @Test
    void finish_archivesOnceAndRunsTournamentHookOnce() {
        MatchPrimary m = live(in(-600_000), in(-1_000));
        m.setStatus("FINISHING");
        m.setScore1(1);
        m.setTournamentId("t1");
        m.setProblems(List.of("https://codeforces.com/contest/1/problem/A"));
        m.setCurIdx(1);
        when(cf.fetchSolve(anyString(), anyString(), any())).thenReturn(null);
        arrangeFreeze(m);
        when(secondaryRepo.save(any(MatchSecondary.class))).thenAnswer(inv -> inv.getArgument(0));

        MatchPrimary scheduled = live(in(-600_000), in(-1_000));
        scheduled.setProblems(m.getProblems());
        scheduled.setCurIdx(1);
        service.processMatch(scheduled);

        ArgumentCaptor<MatchSecondary> saved = ArgumentCaptor.forClass(MatchSecondary.class);
        verify(secondaryRepo).save(saved.capture());
        assertEquals("alice", saved.getValue().getWinnerId());
        assertEquals("FINISHED", saved.getValue().getStatus());
        assertEquals("ALL_PROBLEMS_SOLVED", saved.getValue().getEndedReason());
        verify(tournaments, times(1)).onMatchFinished("t1", "ABC123", "alice");
        verify(primaryRepo).deleteById("m1");
    }

    @Test
    void finish_duplicateResultIsSkipped_noSecondTournamentAdvance() {
        MatchPrimary m = live(in(-600_000), in(-1_000));
        m.setStatus("FINISHING");
        m.setTournamentId("t1");
        m.setCurIdx(2);
        arrangeFreeze(m);
        when(secondaryRepo.save(any(MatchSecondary.class))).thenThrow(new DuplicateKeyException("already archived"));
        MatchSecondary existing = new MatchSecondary();
        existing.setWinnerId("DRAW");
        when(secondaryRepo.findByInviteCode("ABC123")).thenReturn(Optional.of(existing));

        MatchPrimary scheduled = live(in(-600_000), in(-1_000));
        scheduled.setCurIdx(2);
        service.processMatch(scheduled);

        verify(tournaments, never()).onMatchFinished(anyString(), anyString(), anyString());
        verify(primaryRepo).deleteById("m1"); // the leftover live match is still cleaned up
    }

    @Test
    void finish_whenAnotherCallerAlreadyClosedIt_doesNothing() {
        MatchPrimary scheduled = live(in(-600_000), in(-1_000));
        scheduled.setCurIdx(2);
        arrangeFreeze(null);
        when(primaryRepo.findById("m1")).thenReturn(Optional.empty());

        service.processMatch(scheduled);

        verify(secondaryRepo, never()).save(any());
        verify(tournaments, never()).onMatchFinished(anyString(), anyString(), anyString());
    }

    @Test
    void finish_staleFinishingMatchIsRecoveredAfterCrash() {
        MatchPrimary stuck = live(in(-600_000), in(-100_000));
        stuck.setStatus("FINISHING");
        stuck.setCurIdx(0);
        when(secondaryRepo.save(any(MatchSecondary.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(MatchPrimary.class)))
                .thenReturn(null);
        when(primaryRepo.findById("m1")).thenReturn(Optional.of(stuck));

        service.processMatch(stuck);

        ArgumentCaptor<MatchSecondary> saved = ArgumentCaptor.forClass(MatchSecondary.class);
        verify(secondaryRepo).save(saved.capture());
        assertEquals("DRAW", saved.getValue().getWinnerId());
        assertEquals("TIME_EXPIRED", saved.getValue().getEndedReason());
        verify(primaryRepo).deleteById("m1");
    }

    // ───────────────────────── stop ─────────────────────────

    @Test
    void stopMatch_opponentIsForbidden() {
        verifiedUser("bob", "bob@x.com");
        MatchPrimary m = live(in(-1000), in(600_000));
        when(primaryRepo.findByInviteCode("ABC123")).thenReturn(Optional.of(m));

        assertEquals(HttpStatus.FORBIDDEN,
                assertThrows(ResponseStatusException.class, () -> service.stopMatchByCreator("ABC123", "bob@x.com")).getStatusCode());
        verify(mongo, never()).findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(MatchPrimary.class));
    }

    @Test
    void stopMatch_losingTheRaceToDeadlineFinish_isBadRequest() {
        verifiedUser("alice", "alice@x.com");
        MatchPrimary m = live(in(-1000), in(600_000));
        when(primaryRepo.findByInviteCode("ABC123")).thenReturn(Optional.of(m));
        MatchPrimary frozenByScheduler = live(in(-1000), in(600_000));
        frozenByScheduler.setStatus("FINISHING");
        frozenByScheduler.setEndedReason("TIME_EXPIRED");
        arrangeFreeze(frozenByScheduler);

        assertEquals(HttpStatus.BAD_REQUEST,
                assertThrows(ResponseStatusException.class, () -> service.stopMatchByCreator("ABC123", "alice@x.com")).getStatusCode());
        verify(secondaryRepo, never()).save(any());
    }

    @Test
    void stopMatch_creatorStopsOngoingMatch() {
        verifiedUser("alice", "alice@x.com");
        MatchPrimary m = live(in(-1000), in(600_000));
        when(primaryRepo.findByInviteCode("ABC123")).thenReturn(Optional.of(m));
        MatchPrimary frozen = live(in(-1000), in(600_000));
        frozen.setStatus("FINISHING");
        frozen.setEndedReason("CREATOR_STOPPED");
        frozen.setScore2(1);
        arrangeFreeze(frozen);
        when(secondaryRepo.save(any(MatchSecondary.class))).thenAnswer(inv -> inv.getArgument(0));

        MatchSecondary result = service.stopMatchByCreator("ABC123", "alice@x.com");

        assertEquals("CREATOR_STOPPED", result.getEndedReason());
        assertEquals("bob", result.getWinnerId());
    }

    // ───────────────────────── skip votes ─────────────────────────

    private UpdateResult matched(long n) {
        return UpdateResult.acknowledged(n, n, null);
    }

    @Test
    void skipVote_fromNonParticipantIsIgnored() {
        when(primaryRepo.findByInviteCode("ABC123")).thenReturn(Optional.of(live(in(-1000), in(600_000))));

        service.handleSkipVote("ABC123", "mallory");

        verify(mongo, never()).updateFirst(any(Query.class), any(Update.class), eq(MatchPrimary.class));
    }

    @Test
    void skipVote_singleVoteDoesNotAdvance() {
        MatchPrimary m = live(in(-1000), in(600_000));
        when(primaryRepo.findByInviteCode("ABC123")).thenReturn(Optional.of(m));
        when(primaryRepo.findById("m1")).thenReturn(Optional.of(m));
        // 1st update (record vote) matches, 2nd (both-voted advance) does not
        when(mongo.updateFirst(any(Query.class), any(Update.class), eq(MatchPrimary.class)))
                .thenReturn(matched(1))
                .thenReturn(UpdateResult.acknowledged(0, 0L, null));

        service.handleSkipVote("ABC123", "alice");

        verify(mongo, times(2)).updateFirst(any(Query.class), any(Update.class), eq(MatchPrimary.class));
        verify(ws).convertAndSend(eq("/topic/match/ABC123/skip"), any(Object.class));
    }

    @Test
    void skipVote_bothVotesAdvanceExactlyOneProblem() {
        MatchPrimary m = live(in(-1000), in(600_000));
        when(primaryRepo.findByInviteCode("ABC123")).thenReturn(Optional.of(m));
        when(primaryRepo.findById("m1")).thenReturn(Optional.of(m));
        when(mongo.updateFirst(any(Query.class), any(Update.class), eq(MatchPrimary.class))).thenReturn(matched(1));

        service.handleSkipVote("ABC123", "bob");

        ArgumentCaptor<Update> upd = ArgumentCaptor.forClass(Update.class);
        verify(mongo, times(2)).updateFirst(any(Query.class), upd.capture(), eq(MatchPrimary.class));
        String advance = upd.getAllValues().get(1).getUpdateObject().toJson();
        assertTrue(advance.contains("SKIPPED") && advance.contains("curIdx"), advance);
    }

    @Test
    void skipVote_forAnAlreadyAdvancedProblemIsDropped() {
        MatchPrimary m = live(in(-1000), in(600_000));
        when(primaryRepo.findByInviteCode("ABC123")).thenReturn(Optional.of(m));
        when(mongo.updateFirst(any(Query.class), any(Update.class), eq(MatchPrimary.class)))
                .thenReturn(UpdateResult.acknowledged(0, 0L, null));

        service.handleSkipVote("ABC123", "alice");

        verify(mongo, times(1)).updateFirst(any(Query.class), any(Update.class), eq(MatchPrimary.class));
        verify(ws, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void skipVote_unknownMatchDoesNotThrow() {
        when(primaryRepo.findByInviteCode("NOPE")).thenReturn(Optional.empty());
        service.handleSkipVote("NOPE", "alice");
        verify(mongo, never()).updateFirst(any(Query.class), any(Update.class), eq(MatchPrimary.class));
    }

    // ───────────────────────── participant / account resolution ─────────────────────────

    @Test
    void resolveParticipantHandle_onlyForPlayersOfThatMatch() {
        verifiedUser("alice", "alice@x.com");
        verifiedUser("mallory", "mallory@x.com");
        when(primaryRepo.findByInviteCode("ABC123")).thenReturn(Optional.of(live(in(-1000), in(600_000))));

        assertEquals("alice", service.resolveParticipantHandle("alice@x.com", "ABC123"));
        assertNull(service.resolveParticipantHandle("mallory@x.com", "ABC123"));
        assertNull(service.resolveParticipantHandle("ghost@x.com", "ABC123"));
        assertNull(service.resolveParticipantHandle(null, "ABC123"));
        assertNull(service.resolveParticipantHandle("alice@x.com", " "));
    }

    @Test
    void resolveParticipantHandle_worksForLobbyAndRejectsUnknownCode() {
        verifiedUser("bob", "bob@x.com");
        when(primaryRepo.findByInviteCode("ABC123")).thenReturn(Optional.empty());
        when(secondaryRepo.findByInviteCode("ABC123")).thenReturn(Optional.of(lobby("READY", "bob")));
        when(secondaryRepo.findByInviteCode("ZZZZZZ")).thenReturn(Optional.empty());
        when(primaryRepo.findByInviteCode("ZZZZZZ")).thenReturn(Optional.empty());

        assertEquals("bob", service.resolveParticipantHandle("bob@x.com", "ABC123"));
        assertNull(service.resolveParticipantHandle("bob@x.com", "ZZZZZZ"));
    }

    @Test
    void isSameAccount_matchesOwnIdentifiersOnly() {
        verifiedUser("alice", "alice@x.com");
        assertTrue(service.isSameAccount("alice@x.com", "alice@x.com"));
        assertTrue(service.isSameAccount("alice@x.com", "ALICE"));
        assertTrue(service.isSameAccount("alice@x.com", "id-alice"));
        assertFalse(service.isSameAccount("alice@x.com", "bob"));
        assertFalse(service.isSameAccount("alice@x.com", ""));
        assertFalse(service.isSameAccount(null, "alice"));
    }

    // ───────────────────────── infra ─────────────────────────

    private interface IndexedTask { void run(int i) throws Exception; }

    private void runConcurrently(int n, IndexedTask task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch go = new CountDownLatch(1);
        ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < n; i++) {
            final int idx = i;
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    task.run(idx);
                } catch (Throwable t) {
                    errors.add(t);
                }
            });
        }
        ready.await();
        go.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(20, TimeUnit.SECONDS));
        assertTrue(errors.isEmpty(), "unexpected errors: " + errors);
    }

    // ───────────────────────── tie-break / Codeforces outage ─────────────────────────

    @Test
    void sameSecondSolve_lowerCodeforcesSubmissionIdWins() {
        MatchPrimary m = live(in(-60_000), in(600_000));
        Date sameSecond = in(-5_000);
        when(cf.fetchSolve(eq("alice"), anyString(), any())).thenReturn(new CodeforcesService.SolveInfo(sameSecond, 2002L));
        when(cf.fetchSolve(eq("bob"), anyString(), any())).thenReturn(new CodeforcesService.SolveInfo(sameSecond, 2001L));
        arrangeScoreUpdate();

        service.processMatch(m);

        ArgumentCaptor<Update> upd = ArgumentCaptor.forClass(Update.class);
        verify(mongo).updateFirst(any(Query.class), upd.capture(), eq(MatchPrimary.class));
        assertTrue(upd.getValue().getUpdateObject().toJson().contains("score2"));
    }

    @Test
    void codeforcesOutage_neverScoresOrFinishesBeforeDeadline() {
        MatchPrimary m = live(in(-60_000), in(600_000));
        when(cf.fetchSolve(anyString(), anyString(), any()))
                .thenThrow(new CodeforcesService.CodeforcesUnavailableException("down", null));

        service.processMatch(m);

        verify(mongo, never()).updateFirst(any(Query.class), any(Update.class), eq(MatchPrimary.class));
        verify(mongo, never()).findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(MatchPrimary.class));
    }

    @Test
    void codeforcesOutage_justAfterDeadline_keepsMatchOpenForRetry() {
        MatchPrimary m = live(in(-600_000), in(-5_000)); // ended 5 s ago
        when(cf.fetchSolve(anyString(), anyString(), any()))
                .thenThrow(new CodeforcesService.CodeforcesUnavailableException("down", null));

        service.processMatch(m);

        verify(mongo, never()).findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(MatchPrimary.class));
        verify(secondaryRepo, never()).save(any());
    }

    @Test
    void codeforcesOutage_afterGracePeriod_finishesHonestlyWithoutInventingAWinner() {
        MatchPrimary m = live(in(-3_600_000), in(-600_000)); // ended 10 min ago, CF still down
        when(cf.fetchSolve(anyString(), anyString(), any()))
                .thenThrow(new CodeforcesService.CodeforcesUnavailableException("down", null));
        MatchPrimary frozen = live(in(-3_600_000), in(-600_000));
        frozen.setStatus("FINISHING");
        arrangeFreeze(frozen);
        when(secondaryRepo.save(any(MatchSecondary.class))).thenAnswer(inv -> inv.getArgument(0));

        service.processMatch(m);

        verify(mongo, never()).updateFirst(any(Query.class), any(Update.class), eq(MatchPrimary.class));
        ArgumentCaptor<MatchSecondary> saved = ArgumentCaptor.forClass(MatchSecondary.class);
        verify(secondaryRepo).save(saved.capture());
        assertEquals("DRAW", saved.getValue().getWinnerId()); // 0-0: nobody is credited without evidence
        assertEquals("TIME_EXPIRED", saved.getValue().getEndedReason());
    }

    // ───────────────────────── join / lobby lifecycle ─────────────────────────

    @Test
    void joinMatch_sameSecondPlayerAgain_isIdempotent_refreshDoesNotRemoveOrDuplicate() {
        verifiedUser("bob", "bob@x.com");
        MatchSecondary ready = lobby("READY", "bob");
        when(secondaryRepo.findByInviteCode("ABC123")).thenReturn(Optional.of(ready));

        assertSame(ready, service.joinMatch("bob", "ABC123"));
        verify(mongo, never()).findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(MatchSecondary.class));
        verify(secondaryRepo, never()).save(any());
    }

    @Test
    void joinMatch_thirdPlayerCannotTakeAFullLobby() {
        verifiedUser("dave", "dave@x.com");
        when(secondaryRepo.findByInviteCode("ABC123")).thenReturn(Optional.of(lobby("READY", "bob")));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.joinMatch("dave", "ABC123"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(mongo, never()).findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(MatchSecondary.class));
    }

    @Test
    void joinMatch_inviteCodeIsCaseInsensitive_andHostIsNotifiedAfterPersisting() {
        verifiedUser("bob", "bob@x.com");
        when(secondaryRepo.findByInviteCode("ABC123")).thenReturn(Optional.of(lobby("WAITING", null)));
        MatchSecondary joined = lobby("READY", "bob");
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(MatchSecondary.class)))
                .thenReturn(joined);

        service.joinMatch("bob", "  abc123 ");

        verify(ws).convertAndSend("/topic/match/ABC123", joined);
    }

    @Test
    void joinMatch_noNotificationWhenTheJoinLostTheRace() {
        verifiedUser("bob", "bob@x.com");
        when(secondaryRepo.findByInviteCode("ABC123")).thenReturn(Optional.of(lobby("WAITING", null)));
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(MatchSecondary.class)))
                .thenReturn(null);

        assertThrows(ResponseStatusException.class, () -> service.joinMatch("bob", "ABC123"));
        verify(ws, never()).convertAndSend(anyString(), any(Object.class));
    }

    private static String idCreatedHoursAgo(int hours) {
        return new org.bson.types.ObjectId(new Date(System.currentTimeMillis() - hours * 3_600_000L)).toHexString();
    }

    @Test
    void joinAndStart_expiredLobbyIsGone() {
        verifiedUser("alice", "alice@x.com");
        verifiedUser("bob", "bob@x.com");

        MatchSecondary old = lobby("WAITING", null);
        old.setId(idCreatedHoursAgo(5));
        when(secondaryRepo.findByInviteCode("ABC123")).thenReturn(Optional.of(old));
        assertEquals(HttpStatus.GONE,
                assertThrows(ResponseStatusException.class, () -> service.joinMatch("bob", "ABC123")).getStatusCode());

        MatchSecondary oldReady = lobby("READY", "bob");
        oldReady.setId(idCreatedHoursAgo(5));
        when(secondaryRepo.findByInviteCode("ABC123")).thenReturn(Optional.of(oldReady));
        assertEquals(HttpStatus.GONE,
                assertThrows(ResponseStatusException.class, () -> service.startMatch("alice", "ABC123")).getStatusCode());
    }

    @Test
    void freshLobbyIsNotExpired_andTournamentLobbiesNeverExpire() {
        verifiedUser("bob", "bob@x.com");
        MatchSecondary fresh = lobby("WAITING", null);
        fresh.setId(idCreatedHoursAgo(1));
        when(secondaryRepo.findByInviteCode("ABC123")).thenReturn(Optional.of(fresh));
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(MatchSecondary.class)))
                .thenReturn(lobby("READY", "bob"));
        service.joinMatch("bob", "ABC123"); // no exception
    }

    @Test
    void startMatch_codeIsCaseInsensitive() {
        arrangeStartable();
        when(mongo.findAndRemove(any(Query.class), eq(MatchSecondary.class))).thenReturn(lobby("READY", "bob"));
        assertEquals("ONGOING", service.startMatch("alice", " abc123").getStatus());
    }

    // ───────────────────────── who may see a match ─────────────────────────

    @Test
    void canViewMatch_playersOrganizerAndAdminsOnly() {
        verifiedUser("alice", "alice@x.com");
        verifiedUser("bob", "bob@x.com");
        verifiedUser("eve", "eve@x.com");
        User admin = verifiedUser("root", "root@x.com");
        admin.setAdmin(true);
        User organizer = verifiedUser("carol", "carol@x.com");

        MatchPrimary m = live(in(-1000), in(600_000));
        m.setTournamentId("t1");
        when(primaryRepo.findByInviteCode("ABC123")).thenReturn(Optional.of(m));
        CF_DuelProject.CF_DuelProject.model.Tournament t = new CF_DuelProject.CF_DuelProject.model.Tournament();
        t.setCreatorId("carol");
        when(tournamentRepo.findById("t1")).thenReturn(Optional.of(t));

        assertTrue(service.canViewMatch("alice@x.com", "ABC123"));
        assertTrue(service.canViewMatch("bob@x.com", "abc123"));
        assertTrue(service.canViewMatch("carol@x.com", "ABC123"), "tournament organizer");
        assertTrue(service.canViewMatch("root@x.com", "ABC123"), "admin");
        assertFalse(service.canViewMatch("eve@x.com", "ABC123"));
        assertFalse(service.canViewMatch("nobody@x.com", "ABC123"));
        assertFalse(service.canViewMatch(null, "ABC123"));
        assertFalse(service.canViewMatch("alice@x.com", "UNKNOWN"));
    }

    @Test
    void canViewMatch_worksByMatchIdToo() {
        verifiedUser("alice", "alice@x.com");
        MatchPrimary m = live(in(-1000), in(600_000));
        when(primaryRepo.findByInviteCode("M1")).thenReturn(Optional.empty());
        when(primaryRepo.findById("m1")).thenReturn(Optional.of(m));
        assertTrue(service.canViewMatch("alice@x.com", "m1"));
    }

    @Test
    void matchStatus_includesServerTime_andIsDeniedToNonParticipants() {
        verifiedUser("alice", "alice@x.com");
        verifiedUser("eve", "eve@x.com");
        MatchPrimary m = live(in(-1000), in(600_000));
        when(primaryRepo.findByInviteCode("ABC123")).thenReturn(Optional.of(m));

        Map<String, Object> ok = service.getMatchStatusFor("ABC123", "alice@x.com");
        assertEquals("ONGOING", ok.get("status"));
        assertTrue(ok.get("serverTime") instanceof Date);
        assertEquals(m.getProblems(), ok.get("problems"));

        assertEquals(HttpStatus.FORBIDDEN,
                assertThrows(ResponseStatusException.class, () -> service.getMatchStatusFor("ABC123", "eve@x.com")).getStatusCode());
    }

    @Test
    void bothPlayersReadIdenticalAuthoritativeState() {
        verifiedUser("alice", "alice@x.com");
        verifiedUser("bob", "bob@x.com");
        MatchPrimary m = live(in(-1000), in(600_000));
        m.setScore1(1);
        m.setCurIdx(1);
        when(primaryRepo.findByInviteCode("ABC123")).thenReturn(Optional.of(m));

        Map<String, Object> a = new java.util.LinkedHashMap<>(service.getMatchStatusFor("ABC123", "alice@x.com"));
        Map<String, Object> b = new java.util.LinkedHashMap<>(service.getMatchStatusFor("ABC123", "bob@x.com"));
        a.remove("serverTime");
        b.remove("serverTime");
        assertEquals(a, b); // same id, players, problems+order, start/end, scores, current problem, status
    }

    // ───────────────────────── stopping tournament matches ─────────────────────────

    @Test
    void stopMatch_playerCannotStopATournamentMatch_butTheOrganizerCan() {
        verifiedUser("alice", "alice@x.com");
        User organizer = verifiedUser("carol", "carol@x.com");
        MatchPrimary m = live(in(-1000), in(600_000));
        m.setTournamentId("t1");
        when(primaryRepo.findByInviteCode("ABC123")).thenReturn(Optional.of(m));
        CF_DuelProject.CF_DuelProject.model.Tournament t = new CF_DuelProject.CF_DuelProject.model.Tournament();
        t.setCreatorId("carol");
        when(tournamentRepo.findById("t1")).thenReturn(Optional.of(t));

        assertEquals(HttpStatus.FORBIDDEN,
                assertThrows(ResponseStatusException.class, () -> service.stopMatchByCreator("ABC123", "alice@x.com")).getStatusCode());

        MatchPrimary frozen = live(in(-1000), in(600_000));
        frozen.setStatus("FINISHING");
        frozen.setEndedReason("CREATOR_STOPPED");
        frozen.setTournamentId("t1");
        arrangeFreeze(frozen);
        when(secondaryRepo.save(any(MatchSecondary.class))).thenAnswer(inv -> inv.getArgument(0));

        assertEquals("CREATOR_STOPPED", service.stopMatchByCreator("ABC123", "carol@x.com").getEndedReason());
        verify(tournaments, times(1)).onMatchFinished(eq("t1"), eq("ABC123"), anyString());
    }
}
