package CF_DuelProject.CF_DuelProject;

import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import CF_DuelProject.CF_DuelProject.model.CFVerification;
import CF_DuelProject.CF_DuelProject.model.MatchPrimary;
import CF_DuelProject.CF_DuelProject.model.MatchSecondary;
import CF_DuelProject.CF_DuelProject.model.Tournament;
import CF_DuelProject.CF_DuelProject.model.User;
import CF_DuelProject.CF_DuelProject.repository.CFVerificationRepository;
import CF_DuelProject.CF_DuelProject.repository.PrimaryMatchRepository;
import CF_DuelProject.CF_DuelProject.repository.SecondaryMatchRepository;
import CF_DuelProject.CF_DuelProject.repository.TournamentRepository;
import CF_DuelProject.CF_DuelProject.repository.UserRepository;
import CF_DuelProject.CF_DuelProject.service.CFVerificationService;

import CF_DuelProject.CF_DuelProject.service.MatchService;
import CF_DuelProject.CF_DuelProject.service.TournamentService;
import CF_DuelProject.CF_DuelProject.service.UserService;

/**
 * Legacy tests that read and WRITE a real MongoDB (they delete users by handle, save matches and tournaments).
 * They are therefore OFF by default and can only run against an explicitly provided, isolated test database:
 *
 *   TEST_MONGODB_URI=<throwaway cluster/database URI>  TEST_MONGODB_DATABASE=cf_duel_test_<anything>
 *
 * The competition database (MONGODB_URI / MONGODB_DATABASE) is never used by these tests.
 */
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "TEST_MONGODB_URI", matches = ".+")
@SpringBootTest
class CfDuelProjectApplicationTests {

    @org.springframework.test.context.DynamicPropertySource
    static void isolatedDatabase(org.springframework.test.context.DynamicPropertyRegistry registry) {
        String uri = System.getenv("TEST_MONGODB_URI");
        String db = System.getenv("TEST_MONGODB_DATABASE");
        String competitionDb = System.getenv("MONGODB_DATABASE");
        if (db == null || !db.startsWith("cf_duel_test") || db.equals(competitionDb)) {
            throw new IllegalStateException("Refusing to run: TEST_MONGODB_DATABASE must start with 'cf_duel_test' and differ from MONGODB_DATABASE");
        }
        registry.add("spring.data.mongodb.uri", () -> uri);
        registry.add("spring.data.mongodb.database", () -> db);
        registry.add("JWT_SECRET", () -> "integration-test-only-secret-integration-test-only-secret-1234567");
        registry.add("spring.security.oauth2.client.registration.google.client-id", () -> "test-client-id");
        registry.add("spring.security.oauth2.client.registration.google.client-secret", () -> "test-client-secret");
    }

    
    @Autowired
    private MatchService matchService;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PrimaryMatchRepository primaryMatchRepository;

    @Autowired
    private SecondaryMatchRepository secondaryMatchRepository;

    @Autowired
    private TournamentRepository tournamentRepository;

    @Autowired
    private TournamentService tournamentService;

    @Autowired
    private CFVerificationService cfVerificationService;

    @Autowired
    private CFVerificationRepository cfVerificationRepository;

    private RestTemplate originalRestTemplate;

    @BeforeEach
    void cleanUpTestUsers() {
        originalRestTemplate = new RestTemplate();
        cfVerificationService.setRestTemplate(originalRestTemplate);

        // Clean up test handles so tests run deterministically
        userRepository.findByCodeforcesHandleIgnoreCase("tourist").ifPresent(userRepository::delete);
        userRepository.findByCodeforcesHandleIgnoreCase("jiangly").ifPresent(userRepository::delete);
        userRepository.findByCodeforcesHandleIgnoreCase("Benq").ifPresent(userRepository::delete);
        userRepository.findByCodeforcesHandleIgnoreCase("Petr").ifPresent(userRepository::delete);
    }


    @AfterEach
    void tearDown() {
        cfVerificationService.setRestTemplate(originalRestTemplate);
    }

    private Map<String, Object> createSubmission(long id, long creationTimeSeconds, String handle, int contestId, String index, String verdict) {
        Map<String, Object> sub = new HashMap<>();
        sub.put("id", id);
        sub.put("creationTimeSeconds", creationTimeSeconds);
        sub.put("verdict", verdict);

        Map<String, Object> member = new HashMap<>();
        member.put("handle", handle);
        Map<String, Object> author = new HashMap<>();
        author.put("members", List.of(member));
        sub.put("author", author);

        Map<String, Object> problem = new HashMap<>();
        problem.put("contestId", contestId);
        problem.put("index", index);
        problem.put("name", "Watermelon");
        sub.put("problem", problem);

        return sub;
    }

    @Test
    void contextLoads() {

        Assertions.assertNotNull(matchService);
        Assertions.assertNotNull(userService);
    }

    

    @Test
    @Disabled("Destructive: deleteAll() on users/matches/tournaments of whatever MongoDB is configured. Run manually against a throwaway DB only.")
    void purgeAllStoredLoginsAndUsers() {
        userRepository.deleteAll();
        cfVerificationRepository.deleteAll();
        primaryMatchRepository.deleteAll();
        secondaryMatchRepository.deleteAll();
        if (tournamentRepository != null) {
            tournamentRepository.deleteAll();
        }
        System.out.println(">>> PURGED ALL STORED LOGINS, USERS, VERIFICATIONS, MATCHES, AND TOURNAMENTS FROM MONGODB.");
        Assertions.assertEquals(0, userRepository.count());
    }



    

    @Test
    void testStopMatchByCreatorAndOpponentForbidden() {
        long ts = System.currentTimeMillis();
        String hostEmail = "host_" + ts + "@test.com";
        String guestEmail = "guest_" + ts + "@test.com";
        String hostHandle = "Petr";
        String guestHandle = "tourist";

        User host = new User();
        host.setEmail(hostEmail);
        host.setName("Host User");
        host.setPassword("password123");
        host.setCodeforcesHandle(hostHandle);
        host.setCfHandle(hostHandle);
        host.setCodeforcesVerified(true);
        host.setCfVerified(true);
        host.setCodeforcesSubject("cf_" + hostHandle);
        userRepository.save(host);

        User guest = new User();
        guest.setEmail(guestEmail);
        guest.setName("Guest User");
        guest.setPassword("password123");
        guest.setCodeforcesHandle(guestHandle);
        guest.setCfHandle(guestHandle);
        guest.setCodeforcesVerified(true);
        guest.setCfVerified(true);
        guest.setCodeforcesSubject("cf_" + guestHandle);
        userRepository.save(guest);

        // 1. Host creates match
        MatchSecondary created = matchService.createMatch(hostHandle, 15, "800");
        String inviteCode = created.getInviteCode();
        Assertions.assertNotNull(inviteCode);
        Assertions.assertEquals("WAITING", created.getStatus());

        // 2. Guest joins match
        MatchSecondary joined = matchService.joinMatch(guestHandle, inviteCode);
        Assertions.assertEquals("READY", joined.getStatus());

        // 3. Host starts match
        MatchPrimary started = matchService.startMatch(hostHandle, inviteCode);
        Assertions.assertEquals("ONGOING", started.getStatus());
        Assertions.assertTrue(primaryMatchRepository.findByInviteCode(inviteCode).isPresent());

        // 4. Guest (Opponent) attempts to stop match -> MUST FAIL WITH 403 FORBIDDEN
        ResponseStatusException ex = Assertions.assertThrows(ResponseStatusException.class, () -> {
            matchService.stopMatchByCreator(inviteCode, guestEmail);
        });
        Assertions.assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        Assertions.assertTrue(ex.getReason().contains("Only the match creator can stop the match"));

        // Also test by guest CF handle
        ResponseStatusException exHandle = Assertions.assertThrows(ResponseStatusException.class, () -> {
            matchService.stopMatchByCreator(inviteCode, guestHandle);
        });
        Assertions.assertEquals(HttpStatus.FORBIDDEN, exHandle.getStatusCode());

        // 5. Host (Creator) stops match -> MUST SUCCEED
        MatchSecondary stopped = matchService.stopMatchByCreator(inviteCode, hostEmail);
        Assertions.assertNotNull(stopped);
        Assertions.assertEquals("FINISHED", stopped.getStatus());
        Assertions.assertEquals("CREATOR_STOPPED", stopped.getEndedReason());

        // 6. Verify removed from Primary (so scheduler stops polling) and exists in Secondary
        Assertions.assertTrue(primaryMatchRepository.findByInviteCode(inviteCode).isEmpty());
        Optional<MatchSecondary> secMatch = secondaryMatchRepository.findByInviteCode(inviteCode);
        Assertions.assertTrue(secMatch.isPresent());
        Assertions.assertEquals("FINISHED", secMatch.get().getStatus());
        Assertions.assertEquals("CREATOR_STOPPED", secMatch.get().getEndedReason());

        // 7. Verify match status endpoint payload
        Map<String, Object> statusMap = matchService.getMatchStatus(inviteCode);
        Assertions.assertEquals("FINISHED", statusMap.get("status"));
        Assertions.assertEquals("CREATOR_STOPPED", statusMap.get("endedReason"));
        Assertions.assertEquals("SECONDARY", statusMap.get("source"));

        // 8. Verify user match history
        List<MatchSecondary> history = matchService.getUserMatchHistory(hostEmail);
        Assertions.assertFalse(history.isEmpty());
        MatchSecondary histMatch = history.stream().filter(m -> inviteCode.equals(m.getInviteCode())).findFirst().orElseThrow();
        Assertions.assertEquals("CREATOR_STOPPED", histMatch.getEndedReason());
    }

    @Test
    void testGenerateChallengeSuccessAndVerificationCode() {
        String email = "challenge_gen_" + System.currentTimeMillis() + "@test.com";
        User user = new User();
        user.setEmail(email);
        user.setName("Challenge Tester");
        user.setPassword("password123");
        user.setCfVerified(false);
        user.setCodeforcesVerified(false);
        userRepository.save(user);

        // Generate challenge for valid handle 'tourist'
        Map<String, Object> challenge = cfVerificationService.generateChallenge(user, "tourist");
        Assertions.assertNotNull(challenge);
        Assertions.assertEquals("tourist", challenge.get("cfHandle"));
        Assertions.assertNotNull(challenge.get("verificationCode"));
        Assertions.assertTrue(((String) challenge.get("verificationCode")).startsWith("CF-ARENA-"));
        Assertions.assertEquals(4, challenge.get("contestId"));
        Assertions.assertEquals("A", challenge.get("problemIndex"));
        Assertions.assertEquals("Watermelon", challenge.get("problemName"));
        Assertions.assertNotNull(challenge.get("problemUrl"));
        Assertions.assertNotNull(challenge.get("codeSnippet"));
        Assertions.assertTrue(((String) challenge.get("codeSnippet")).contains((String) challenge.get("verificationCode")));
        Assertions.assertNotNull(challenge.get("expiresAt"));

        // Verify saved in repository
        Optional<CF_DuelProject.CF_DuelProject.model.CFVerification> saved = cfVerificationRepository.findTopByUserIdAndStatusOrderByCreatedAtDesc(user.getId(), "PENDING");
        Assertions.assertTrue(saved.isPresent());
        Assertions.assertEquals("tourist", saved.get().getCfHandle());
        Assertions.assertEquals("PENDING", saved.get().getStatus());
        Assertions.assertEquals(challenge.get("verificationCode"), saved.get().getVerificationCode());
        Assertions.assertEquals(4, saved.get().getContestId());
        Assertions.assertEquals("A", saved.get().getProblemIndex());
    }

    @Test
    void testAlreadyVerifiedUserCannotChangeHandleOrGenerateChallenge() {
        String email = "perm_linked_" + System.currentTimeMillis() + "@test.com";
        User user = new User();
        user.setEmail(email);
        user.setName("Permanent User");
        user.setPassword("password123");
        user.setCfHandle("tourist");
        user.setCodeforcesHandle("tourist");
        user.setCfVerified(true);
        user.setCodeforcesVerified(true);
        user.setCfVerifiedAt(Instant.now());
        userRepository.save(user);

        // Attempting to generate a new challenge or change CF account must throw 400 Bad Request
        ResponseStatusException ex = Assertions.assertThrows(ResponseStatusException.class, () -> {
            cfVerificationService.generateChallenge(user, "jiangly");
        });
        Assertions.assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        Assertions.assertTrue(ex.getReason().contains("already verified and cannot be changed"));
    }

    @Test
    void testDuplicateLinkedHandleRejectionInChallengeGeneration() {
        long ts = System.currentTimeMillis();
        String email1 = "user1_" + ts + "@test.com";
        String email2 = "user2_" + ts + "@test.com";

        // User 1 already verified with 'tourist'
        User user1 = new User();
        user1.setEmail(email1);
        user1.setName("User One");
        user1.setPassword("pass");
        user1.setCfHandle("tourist");
        user1.setCodeforcesHandle("tourist");
        user1.setCfVerified(true);
        user1.setCodeforcesVerified(true);
        userRepository.save(user1);

        // User 2 tries to link 'tourist'
        User user2 = new User();
        user2.setEmail(email2);
        user2.setName("User Two");
        user2.setPassword("pass");
        user2.setCfVerified(false);
        userRepository.save(user2);

        ResponseStatusException ex = Assertions.assertThrows(ResponseStatusException.class, () -> {
            cfVerificationService.generateChallenge(user2, "tourist");
        });
        Assertions.assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        Assertions.assertTrue(ex.getReason().contains("already linked to another CF Arena account"));
    }

    @Test
    void testUnverifiedUserCannotParticipateOrRegister() {
        String email = "unverified_test_" + System.currentTimeMillis() + "@test.com";
        User user = new User();
        user.setEmail(email);
        user.setName("Unverified Tester");
        user.setPassword("password123");
        user.setCfVerified(false);
        user.setCodeforcesVerified(false);
        userRepository.save(user);

        // Cannot get verified handle
        ResponseStatusException ex = Assertions.assertThrows(ResponseStatusException.class, () -> {
            userService.getVerifiedCfHandleByEmail(email);
        });
        Assertions.assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        Assertions.assertTrue(ex.getReason().contains("not verified"));
    }

    @Test
    void testGetActiveChallengeReturnsSnippetAndProblem() {
        String email = "active_ch_" + System.currentTimeMillis() + "@test.com";
        User user = new User();
        user.setEmail(email);
        user.setName("Active Challenge Tester");
        user.setPassword("password123");
        user.setCfVerified(false);
        user.setCodeforcesVerified(false);
        userRepository.save(user);

        cfVerificationService.generateChallenge(user, "tourist");

        Map<String, Object> active = cfVerificationService.getActiveChallenge(user);
        Assertions.assertTrue((Boolean) active.get("active"));
        Assertions.assertEquals("tourist", active.get("cfHandle"));
        Assertions.assertEquals(4, active.get("contestId"));
        Assertions.assertEquals("A", active.get("problemIndex"));
        Assertions.assertNotNull(active.get("codeSnippet"));
        Assertions.assertTrue(((String) active.get("codeSnippet")).contains("cf_arena_verification_error"));
    }

    @Test
    void testCheckVerificationSuccessOnLatestCompilationError() {
        String email = "verify_ce_" + System.currentTimeMillis() + "@test.com";
        User user = new User();
        user.setEmail(email);
        user.setName("CE Tester");
        user.setPassword("password123");
        user.setCfVerified(false);
        user.setCodeforcesVerified(false);
        userRepository.save(user);

        cfVerificationService.generateChallenge(user, "tourist");

        // Mock Codeforces user.status returning a valid COMPILATION_ERROR submission on 4A
        Map<String, Object> sub = createSubmission(5001L, 1700000000L, "tourist", 4, "A", "COMPILATION_ERROR");
        RestTemplate mockRestTemplate = Mockito.mock(RestTemplate.class);
        Mockito.when(mockRestTemplate.getForObject(Mockito.contains("user.status"), Mockito.eq(Map.class)))
                .thenReturn(Map.of("status", "OK", "result", List.of(sub)));
        cfVerificationService.setRestTemplate(mockRestTemplate);

        Map<String, Object> result = cfVerificationService.checkVerification(user);
        Assertions.assertNotNull(result);
        Assertions.assertEquals(true, result.get("verified"));
        Assertions.assertEquals("tourist", result.get("handle"));
        Assertions.assertEquals(5001L, result.get("submissionId"));

        User verifiedUser = userRepository.findByEmail(email).orElseThrow();
        Assertions.assertTrue(verifiedUser.isCfVerified());
        Assertions.assertTrue(verifiedUser.isCodeforcesVerified());
        Assertions.assertEquals("tourist", verifiedUser.getCfHandle());
        Assertions.assertEquals("tourist", verifiedUser.getCodeforcesHandle());
    }

    @Test
    void testCheckVerificationEvaluatesLatestWhenMultipleSubmissionsExist() {
        String email = "verify_latest_" + System.currentTimeMillis() + "@test.com";
        User user = new User();
        user.setEmail(email);
        user.setName("Latest CE Tester");
        user.setPassword("password123");
        user.setCfVerified(false);
        user.setCodeforcesVerified(false);
        userRepository.save(user);

        cfVerificationService.generateChallenge(user, "tourist");

        // Older submission is OK, latest submission is CE
        Map<String, Object> oldSub = createSubmission(100L, 1000L, "tourist", 4, "A", "OK");
        Map<String, Object> newSub = createSubmission(200L, 2000L, "tourist", 4, "A", "COMPILATION_ERROR");

        RestTemplate mockRestTemplate = Mockito.mock(RestTemplate.class);
        Mockito.when(mockRestTemplate.getForObject(Mockito.contains("user.status"), Mockito.eq(Map.class)))
                .thenReturn(Map.of("status", "OK", "result", List.of(oldSub, newSub)));
        cfVerificationService.setRestTemplate(mockRestTemplate);

        Map<String, Object> result = cfVerificationService.checkVerification(user);
        Assertions.assertEquals(true, result.get("verified"));
        Assertions.assertEquals(200L, result.get("submissionId"));
    }

    @Test
    void testCheckVerificationFailsWhenLatestIsAccepted() {
        String email = "verify_latest_ac_" + System.currentTimeMillis() + "@test.com";
        User user = new User();
        user.setEmail(email);
        user.setName("Latest AC Tester");
        user.setPassword("password123");
        user.setCfVerified(false);
        user.setCodeforcesVerified(false);
        userRepository.save(user);

        cfVerificationService.generateChallenge(user, "tourist");

        // Older submission was CE, latest is OK/Accepted
        Map<String, Object> oldSub = createSubmission(100L, 1000L, "tourist", 4, "A", "COMPILATION_ERROR");
        Map<String, Object> newSub = createSubmission(200L, 2000L, "tourist", 4, "A", "OK");

        RestTemplate mockRestTemplate = Mockito.mock(RestTemplate.class);
        Mockito.when(mockRestTemplate.getForObject(Mockito.contains("user.status"), Mockito.eq(Map.class)))
                .thenReturn(Map.of("status", "OK", "result", List.of(oldSub, newSub)));
        cfVerificationService.setRestTemplate(mockRestTemplate);

        ResponseStatusException ex = Assertions.assertThrows(ResponseStatusException.class, () -> {
            cfVerificationService.checkVerification(user);
        });
        Assertions.assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        Assertions.assertTrue(ex.getReason().contains("not a Compilation Error"));
    }

    @Test
    void testCheckVerificationFailsWhenLatestIsWrongAnswer() {
        String email = "verify_wa_" + System.currentTimeMillis() + "@test.com";
        User user = new User();
        user.setEmail(email);
        user.setName("WA Tester");
        user.setPassword("password123");
        user.setCfVerified(false);
        user.setCodeforcesVerified(false);
        userRepository.save(user);

        cfVerificationService.generateChallenge(user, "tourist");

        Map<String, Object> sub = createSubmission(300L, 3000L, "tourist", 4, "A", "WRONG_ANSWER");
        RestTemplate mockRestTemplate = Mockito.mock(RestTemplate.class);
        Mockito.when(mockRestTemplate.getForObject(Mockito.contains("user.status"), Mockito.eq(Map.class)))
                .thenReturn(Map.of("status", "OK", "result", List.of(sub)));
        cfVerificationService.setRestTemplate(mockRestTemplate);

        ResponseStatusException ex = Assertions.assertThrows(ResponseStatusException.class, () -> {
            cfVerificationService.checkVerification(user);
        });
        Assertions.assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        Assertions.assertTrue(ex.getReason().contains("not a Compilation Error"));
    }

    @Test
    void testCheckVerificationFailsWhenAuthorHandleMismatches() {
        String email = "verify_mismatch_author_" + System.currentTimeMillis() + "@test.com";
        User user = new User();
        user.setEmail(email);
        user.setName("Mismatch Author Tester");
        user.setPassword("password123");
        user.setCfVerified(false);
        user.setCodeforcesVerified(false);
        userRepository.save(user);

        cfVerificationService.generateChallenge(user, "tourist");

        Map<String, Object> sub = createSubmission(400L, 4000L, "other_account", 4, "A", "COMPILATION_ERROR");
        RestTemplate mockRestTemplate = Mockito.mock(RestTemplate.class);
        Mockito.when(mockRestTemplate.getForObject(Mockito.contains("user.status"), Mockito.eq(Map.class)))
                .thenReturn(Map.of("status", "OK", "result", List.of(sub)));
        cfVerificationService.setRestTemplate(mockRestTemplate);

        ResponseStatusException ex = Assertions.assertThrows(ResponseStatusException.class, () -> {
            cfVerificationService.checkVerification(user);
        });
        Assertions.assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        Assertions.assertTrue(ex.getReason().contains("does not belong to the Codeforces handle"));
    }

    @Test
    void testCheckVerificationFailsWhenProblemMismatches() {
        String email = "verify_wrong_prob_" + System.currentTimeMillis() + "@test.com";
        User user = new User();
        user.setEmail(email);
        user.setName("Wrong Problem Tester");
        user.setPassword("password123");
        user.setCfVerified(false);
        user.setCodeforcesVerified(false);
        userRepository.save(user);

        cfVerificationService.generateChallenge(user, "tourist");

        Map<String, Object> sub = createSubmission(500L, 5000L, "tourist", 1234, "B", "COMPILATION_ERROR");
        RestTemplate mockRestTemplate = Mockito.mock(RestTemplate.class);
        Mockito.when(mockRestTemplate.getForObject(Mockito.contains("user.status"), Mockito.eq(Map.class)))
                .thenReturn(Map.of("status", "OK", "result", List.of(sub)));
        cfVerificationService.setRestTemplate(mockRestTemplate);

        ResponseStatusException ex = Assertions.assertThrows(ResponseStatusException.class, () -> {
            cfVerificationService.checkVerification(user);
        });
        Assertions.assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        Assertions.assertTrue(ex.getReason().contains("assigned problem"));
    }

    @Test
    void testCheckVerificationFailsWhenNoSubmissionsFound() {
        String email = "verify_no_sub_" + System.currentTimeMillis() + "@test.com";
        User user = new User();
        user.setEmail(email);
        user.setName("No Sub Tester");
        user.setPassword("password123");
        user.setCfVerified(false);
        user.setCodeforcesVerified(false);
        userRepository.save(user);

        cfVerificationService.generateChallenge(user, "tourist");

        RestTemplate mockRestTemplate = Mockito.mock(RestTemplate.class);
        Mockito.when(mockRestTemplate.getForObject(Mockito.contains("user.status"), Mockito.eq(Map.class)))
                .thenReturn(Map.of("status", "OK", "result", List.of()));
        cfVerificationService.setRestTemplate(mockRestTemplate);

        ResponseStatusException ex = Assertions.assertThrows(ResponseStatusException.class, () -> {
            cfVerificationService.checkVerification(user);
        });
        Assertions.assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        Assertions.assertTrue(ex.getReason().contains("No Codeforces submission was found yet"));
    }

    @Test
    void testUnverifiedUserTournamentRegistrationForbidden() {
        String unverifiedEmail = "unver_tourn_" + System.currentTimeMillis() + "@test.com";
        User unverifiedUser = new User();
        unverifiedUser.setEmail(unverifiedEmail);
        unverifiedUser.setName("Unverified Tourn Player");
        unverifiedUser.setPassword("pass");
        unverifiedUser.setCfHandle("unverified_player");
        unverifiedUser.setCfVerified(false);
        userRepository.save(unverifiedUser);

        Tournament t = tournamentService.createTournament("host_creator", "Summer Cup", 8, "800", 15);

        ResponseStatusException ex = Assertions.assertThrows(ResponseStatusException.class, () -> {
            tournamentService.registerPlayer(t.getId(), "unverified_player");
        });
        Assertions.assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        Assertions.assertTrue(ex.getReason().contains("Please verify your Codeforces account"));
    }

    @Test
    void testVerifiedUserTournamentRegistrationAllowed() {
        String verifiedEmail = "ver_tourn_" + System.currentTimeMillis() + "@test.com";
        User verifiedUser = new User();
        verifiedUser.setEmail(verifiedEmail);
        verifiedUser.setName("Verified Tourn Player");
        verifiedUser.setPassword("pass");
        verifiedUser.setCfHandle("verified_player");
        verifiedUser.setCfVerified(true);
        verifiedUser.setCodeforcesVerified(true);
        userRepository.save(verifiedUser);

        Tournament t = tournamentService.createTournament("host_creator", "Spring Cup", 8, "800", 15);

        Tournament updated = tournamentService.registerPlayer(t.getId(), "verified_player");
        Assertions.assertTrue(updated.getRegisteredPlayers().contains("verified_player"));
    }

    @Test
    void testTournamentAllRoundMatchesShareSameTiming() {
        // Register 8 verified players
        String creator = "creator_sync";
        User creatorUser = new User();
        creatorUser.setEmail("creator_sync@test.com");
        creatorUser.setCfHandle(creator);
        creatorUser.setCfVerified(true);
        userRepository.save(creatorUser);

        Tournament t = tournamentService.createTournament(creator, "Timing Cup", 8, "800", 20);

        for (int i = 1; i < 8; i++) {
            String pHandle = "player_sync_" + i;
            User pUser = new User();
            pUser.setEmail("p_" + i + "@test.com");
            pUser.setCfHandle(pHandle);
            pUser.setCfVerified(true);
            userRepository.save(pUser);
            tournamentService.registerPlayer(t.getId(), pHandle);
        }

        // Start tournament (round 1 starts)
        Tournament started = tournamentService.startTournament(t.getId(), creator);
        Assertions.assertEquals("ONGOING", started.getStatus());
        Assertions.assertEquals(1, started.getCurrentRound());
        Assertions.assertNotNull(started.getRoundStartTime());
        Assertions.assertNotNull(started.getRoundEndTime());

        Date roundStart = started.getRoundStartTime();
        Date roundEnd = started.getRoundEndTime();

        // Check each match created in round 1
        List<String> r1Codes = started.getMatchCodes().get(1);
        Assertions.assertNotNull(r1Codes);
        Assertions.assertEquals(4, r1Codes.size());

        for (String code : r1Codes) {
            MatchSecondary m = secondaryMatchRepository.findByInviteCode(code).orElseThrow();
            Assertions.assertEquals(started.getId(), m.getTournamentId());
            Assertions.assertEquals(1, m.getBracketRound());
            Assertions.assertEquals(roundStart, m.getStartTime());
            Assertions.assertEquals(roundEnd, m.getEndTime());
        }
    }

    @Test
    void testFinishedTournamentMatchPreservesMetadata() {
        String p1 = "meta_p1";
        String p2 = "meta_p2";

        User u1 = new User();
        u1.setEmail("meta1@test.com");
        u1.setCfHandle(p1);
        u1.setCfVerified(true);
        userRepository.save(u1);

        User u2 = new User();
        u2.setEmail("meta2@test.com");
        u2.setCfHandle(p2);
        u2.setCfVerified(true);
        userRepository.save(u2);

        // Create secondary match representing tournament match
        MatchSecondary lobby = new MatchSecondary();
        lobby.setUser1(p1);
        lobby.setUser2(p2);
        lobby.setPlayer1CFHandle(p1);
        lobby.setPlayer2CFHandle(p2);
        lobby.setDurationMinutes(15);
        lobby.setStatus("READY");
        lobby.setInviteCode("TOUR123");
        lobby.setTournamentId("TOURNAMENT_ABC");
        lobby.setBracketRound(2);
        secondaryMatchRepository.save(lobby);

        // Start match
        MatchPrimary live = matchService.startMatch(p1, "TOUR123");
        Assertions.assertEquals("TOURNAMENT_ABC", live.getTournamentId());
        Assertions.assertEquals(2, live.getBracketRound());

        // Stop match by creator
        MatchSecondary finished = matchService.stopMatchByCreator("TOUR123", "meta1@test.com");
        Assertions.assertNotNull(finished);
        Assertions.assertEquals("FINISHED", finished.getStatus());
        Assertions.assertEquals("CREATOR_STOPPED", finished.getEndedReason());
        Assertions.assertEquals("TOURNAMENT_ABC", finished.getTournamentId());
        Assertions.assertEquals(2, finished.getBracketRound());
        Assertions.assertNotNull(finished.getWinnerId());
    }

    @Test
    void testFinishedMatchStoppedAgainThrowsBadRequest() {
        String hostEmail = "host_stop_again_" + System.currentTimeMillis() + "@test.com";
        String hostHandle = "stop_host";

        User host = new User();
        host.setEmail(hostEmail);
        host.setCfHandle(hostHandle);
        host.setCfVerified(true);
        userRepository.save(host);

        MatchSecondary match = new MatchSecondary();
        match.setUser1(hostHandle);
        match.setPlayer1CFHandle(hostHandle);
        match.setInviteCode("STOPAGAIN");
        match.setStatus("FINISHED");
        match.setEndedReason("CREATOR_STOPPED");
        secondaryMatchRepository.save(match);

        // Stopping an already finished match must throw 400 Bad Request
        ResponseStatusException ex = Assertions.assertThrows(ResponseStatusException.class, () -> {
            matchService.stopMatchByCreator("STOPAGAIN", hostEmail);
        });
        Assertions.assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        Assertions.assertTrue(ex.getReason().contains("already finished"));
    }
}

