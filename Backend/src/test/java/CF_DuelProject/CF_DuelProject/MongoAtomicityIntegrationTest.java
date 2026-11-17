package CF_DuelProject.CF_DuelProject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.mapping.context.MappingContext;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;

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
 * REAL MongoDB integration test of the concurrency guarantees (atomic join / start / finish / skip, unique indexes,
 * survival across a simulated restart). Codeforces, WebSocket and tournament collaborators are mocked.
 *
 * SAFETY — this test is OFF unless both variables are set, and it refuses to run otherwise:
 *   TEST_MONGODB_URI       URI of a throwaway database user/cluster
 *   TEST_MONGODB_DATABASE  must start with "cf_duel_test", must differ from MONGODB_DATABASE, and must be EMPTY
 * It only ever creates collections inside that database and drops that database afterwards.
 * It never reads MONGODB_URI.
 */
@EnabledIfEnvironmentVariable(named = "TEST_MONGODB_URI", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TEST_MONGODB_DATABASE", matches = "cf_duel_test.*")
class MongoAtomicityIntegrationTest {

    private static MongoClient client;
    private static String dbName;
    private static MongoTemplate template;
    private static PrimaryMatchRepository primaryRepo;
    private static SecondaryMatchRepository secondaryRepo;
    private static UserRepository userRepo;

    private final CodeforcesService cf = mock(CodeforcesService.class);
    private final ProblemService problems = mock(ProblemService.class);
    private final TournamentService tournaments = mock(TournamentService.class);
    private final TournamentRepository tournamentRepo = mock(TournamentRepository.class);
    private final SimpMessagingTemplate ws = mock(SimpMessagingTemplate.class);

    @BeforeAll
    static void connectSafely() {
        dbName = System.getenv("TEST_MONGODB_DATABASE");
        String competition = System.getenv("MONGODB_DATABASE");
        if (dbName == null || !dbName.startsWith("cf_duel_test") || dbName.equals(competition) || "cf_duel_db".equals(dbName)) {
            throw new IllegalStateException("Unsafe test database name: refusing to run");
        }
        client = MongoClients.create(System.getenv("TEST_MONGODB_URI"));
        if (client.getDatabase(dbName).listCollectionNames().iterator().hasNext()) {
            client.close();
            throw new IllegalStateException("Test database '" + dbName + "' is not empty: refusing to run");
        }

        SimpleMongoClientDatabaseFactory factory = new SimpleMongoClientDatabaseFactory(client, dbName);
        template = new MongoTemplate(factory);

        // create exactly the indexes the application would create from its @Indexed annotations
        MappingContext<?, ?> ctx = template.getConverter().getMappingContext();
        MongoPersistentEntityIndexResolver resolver = new MongoPersistentEntityIndexResolver((MongoMappingContext) ctx);
        for (Class<?> type : List.of(MatchPrimary.class, MatchSecondary.class, User.class)) {
            for (IndexDefinition d : resolver.resolveIndexFor(type)) {
                template.indexOps(type).createIndex(d);
            }
        }

        MongoRepositoryFactory repos = new MongoRepositoryFactory(template);
        primaryRepo = repos.getRepository(PrimaryMatchRepository.class);
        secondaryRepo = repos.getRepository(SecondaryMatchRepository.class);
        userRepo = repos.getRepository(UserRepository.class);
    }

    @AfterAll
    static void dropOnlyTheTestDatabase() {
        if (client != null) {
            if (dbName != null && dbName.startsWith("cf_duel_test") && !"cf_duel_db".equals(dbName)) {
                client.getDatabase(dbName).drop();
            }
            client.close();
        }
    }

    private MatchService newService() { // a fresh instance == what a restarted backend would have
        return new MatchService(primaryRepo, secondaryRepo, userRepo, tournamentRepo,
                template, cf, problems, ws, tournaments);
    }

    private void verifiedUser(String handle) {
        User u = new User();
        u.setEmail(handle + "@test.local");
        u.setCfHandle(handle);
        u.setCodeforcesVerified(true);
        userRepo.save(u);
    }

    private void parallel(int n, java.util.function.IntConsumer task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch go = new CountDownLatch(1);
        for (int i = 0; i < n; i++) {
            final int idx = i;
            pool.submit(() -> {
                try { go.await(); task.accept(idx); } catch (InterruptedException ignored) { }
            });
        }
        go.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS));
    }

    @Test
    void uniqueIndexesExistOnInviteCodeAndEmail() {
        assertTrue(hasUniqueIndex("secondary_matches", "inviteCode"));
        assertTrue(hasUniqueIndex("primary_matches", "inviteCode"));
        assertTrue(hasUniqueIndex("users", "email"));
    }

    private boolean hasUniqueIndex(String collection, String field) {
        for (org.bson.Document idx : client.getDatabase(dbName).getCollection(collection).listIndexes()) {
            if (Boolean.TRUE.equals(idx.getBoolean("unique")) && ((org.bson.Document) idx.get("key")).containsKey(field)) return true;
        }
        return false;
    }

    @Test
    void manyConcurrentJoinsOnRealMongo_exactlyOneSecondPlayer() throws Exception {
        verifiedUser("host1");
        for (int i = 0; i < 12; i++) verifiedUser("joiner" + i);
        MatchSecondary lobby = newService().createMatch("host1", 30, "EASY");

        AtomicInteger ok = new AtomicInteger();
        parallel(12, i -> {
            try {
                newService().joinMatch("joiner" + i, lobby.getInviteCode());
                ok.incrementAndGet();
            } catch (ResponseStatusException ignored) { }
        });

        assertEquals(1, ok.get());
        MatchSecondary stored = secondaryRepo.findByInviteCode(lobby.getInviteCode()).orElseThrow();
        assertEquals("READY", stored.getStatus());
        assertNotNull(stored.getUser2());
        assertTrue(stored.getUser2().startsWith("joiner"));
    }

    @Test
    void manyConcurrentStartsOnRealMongo_exactlyOnePrimaryMatchAndNoLobbyLeft() throws Exception {
        verifiedUser("host2");
        verifiedUser("guest2");
        MatchService svc = newService();
        MatchSecondary lobby = svc.createMatch("host2", 30, "EASY");
        svc.joinMatch("guest2", lobby.getInviteCode());
        when(problems.getMatchProblems(anyString(), anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of("https://codeforces.com/contest/1/problem/A"));

        AtomicInteger started = new AtomicInteger();
        parallel(10, i -> {
            try {
                newService().startMatch("host2", lobby.getInviteCode());
                started.incrementAndGet();
            } catch (ResponseStatusException ignored) { }
        });

        assertEquals(1, started.get());
        assertEquals(1, template.find(new org.springframework.data.mongodb.core.query.Query(
                org.springframework.data.mongodb.core.query.Criteria.where("inviteCode").is(lobby.getInviteCode())), MatchPrimary.class).size());
        assertTrue(secondaryRepo.findByInviteCode(lobby.getInviteCode()).isEmpty(), "lobby must be consumed by the start");
    }

    @Test
    void finishAndStopRacingOnRealMongo_archiveOnceAndAdvanceTournamentOnce() throws Exception {
        verifiedUser("host3");
        verifiedUser("guest3");
        MatchService svc = newService();
        MatchSecondary lobby = svc.createMatch("host3", 30, "EASY");
        svc.joinMatch("guest3", lobby.getInviteCode());
        when(problems.getMatchProblems(anyString(), anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of("https://codeforces.com/contest/1/problem/A"));
        MatchPrimary live = svc.startMatch("host3", lobby.getInviteCode());
        CF_DuelProject.CF_DuelProject.model.Tournament tour = new CF_DuelProject.CF_DuelProject.model.Tournament();
        tour.setCreatorId("host3");
        when(tournamentRepo.findById("tournament-x")).thenReturn(java.util.Optional.of(tour));
        live.setTournamentId("tournament-x");
        primaryRepo.save(live);
        // make it expired so the scheduler path also wants to finish it
        live.setEndTime(new Date(System.currentTimeMillis() - 1000));
        primaryRepo.save(live);
        when(cf.fetchSolve(anyString(), anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(null);

        parallel(8, i -> {
            try {
                if (i % 2 == 0) newService().processMatch(primaryRepo.findById(live.getId()).orElse(live));
                else newService().stopMatchByCreator(lobby.getInviteCode(), "host3@test.local");
            } catch (RuntimeException ignored) { }
        });

        assertEquals(1, template.find(new org.springframework.data.mongodb.core.query.Query(
                org.springframework.data.mongodb.core.query.Criteria.where("inviteCode").is(lobby.getInviteCode())), MatchSecondary.class).size());
        assertTrue(primaryRepo.findById(live.getId()).isEmpty(), "live match must be removed");
        verify(tournaments, times(1)).onMatchFinished(anyString(), anyString(), anyString());
    }

    @Test
    void stateSurvivesARestart_aNewServiceInstanceSeesTheSameMatchAndFinishesItAfterTheDeadline() {
        verifiedUser("host4");
        verifiedUser("guest4");
        MatchService before = newService();
        MatchSecondary lobby = before.createMatch("host4", 30, "EASY");
        before.joinMatch("guest4", lobby.getInviteCode());
        when(problems.getMatchProblems(anyString(), anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of("https://codeforces.com/contest/1/problem/A", "https://codeforces.com/contest/2/problem/A"));
        MatchPrimary live = before.startMatch("host4", lobby.getInviteCode());

        MatchService afterRestart = newService(); // brand new instance, nothing in memory
        MatchPrimary reloaded = primaryRepo.findByInviteCode(lobby.getInviteCode()).orElseThrow();
        assertEquals(live.getProblems(), reloaded.getProblems());
        assertEquals(live.getStartTime().getTime() / 1000, reloaded.getStartTime().getTime() / 1000);
        assertEquals(live.getEndTime().getTime() / 1000, reloaded.getEndTime().getTime() / 1000);

        reloaded.setEndTime(new Date(System.currentTimeMillis() - 5000)); // deadline passed while "down"
        primaryRepo.save(reloaded);
        when(cf.fetchSolve(anyString(), anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(null);
        afterRestart.processMatch(primaryRepo.findById(reloaded.getId()).orElseThrow());

        MatchSecondary result = secondaryRepo.findByInviteCode(lobby.getInviteCode()).orElseThrow();
        assertEquals("FINISHED", result.getStatus());
        assertEquals("DRAW", result.getWinnerId());
    }

    @Test
    void duplicateEmailIsRejectedByTheDatabase() {
        User a = new User(); a.setEmail("dup@test.local");
        User b = new User(); b.setEmail("dup@test.local");
        userRepo.save(a);
        assertThrows(org.springframework.dao.DuplicateKeyException.class, () -> userRepo.save(b));
    }
}
