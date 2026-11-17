package CF_DuelProject.CF_DuelProject.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.mongodb.client.result.UpdateResult;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;

import CF_DuelProject.CF_DuelProject.dto.ChatMessage;
import CF_DuelProject.CF_DuelProject.dto.EmoteMessage;
import CF_DuelProject.CF_DuelProject.dto.SolveResult;
import CF_DuelProject.CF_DuelProject.model.MatchPrimary;
import CF_DuelProject.CF_DuelProject.model.MatchSecondary;
import CF_DuelProject.CF_DuelProject.model.Tournament;
import CF_DuelProject.CF_DuelProject.model.User;
import CF_DuelProject.CF_DuelProject.repository.PrimaryMatchRepository;
import CF_DuelProject.CF_DuelProject.repository.SecondaryMatchRepository;
import lombok.RequiredArgsConstructor;

import java.util.LinkedHashMap;
import java.security.SecureRandom;
import lombok.extern.slf4j.Slf4j;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class MatchService {

    private final PrimaryMatchRepository matchRepository;
    private final SecondaryMatchRepository matchRepository2;
    private final CF_DuelProject.CF_DuelProject.repository.UserRepository userRepository;
    private final CF_DuelProject.CF_DuelProject.repository.TournamentRepository tournamentRepository;
    private final MongoTemplate mongoTemplate;
    private final CodeforcesService codeforcesService;
    private final ProblemService problemService;
    private final SimpMessagingTemplate messagingTemplate;
    private final @Lazy TournamentService tournamentService;

    /** An untouched duel lobby (not part of a tournament) can no longer be joined/started after this long. */
    static final long LOBBY_TTL_MS = 3L * 60 * 60 * 1000;
    /** After the deadline, how long a match is kept open while Codeforces is unreachable so a late-polled solve is not lost. */
    static final long CF_OUTAGE_GRACE_MS = 3L * 60 * 1000;

    /** Invite codes are case-insensitive for users; they are stored upper-case. */
    public static String normalizeCode(String code) {
        return code == null ? "" : code.trim().toUpperCase(java.util.Locale.ROOT);
    }

    /** Lobby creation time, taken from the Mongo ObjectId (no schema change needed). */
    private static Date lobbyCreatedAt(MatchSecondary lobby) {
        try {
            return lobby.getId() == null ? null : new org.bson.types.ObjectId(lobby.getId()).getDate();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean lobbyExpired(MatchSecondary lobby) {
        if (lobby.getTournamentId() != null) return false; // tournament lobbies wait for the organizer
        Date created = lobbyCreatedAt(lobby);
        return created != null && System.currentTimeMillis() - created.getTime() > LOBBY_TTL_MS;
    }


    // 📡 WebSocket: publish match update to frontend
    private void publishMatchUpdate(MatchPrimary match) {
        log.debug("📡 WebSocket SEND → /topic/match/{} | Score: {}-{}", match.getId(), match.getScore1(), match.getScore2());
        messagingTemplate.convertAndSend("/topic/match/" + match.getId(), match);
        // Also publish to invite code topic so clients can subscribe by invite code
        if (match.getInviteCode() != null) {
            messagingTemplate.convertAndSend("/topic/match/" + match.getInviteCode(), match);
        }
    }

    // 🚀 MAIN LOGIC
    public void processMatch(MatchPrimary match) {

        // A match frozen by an earlier finish attempt that never completed: finish the archive now.
        if ("FINISHING".equals(match.getStatus())) {
            finishMatch(match.getId(), null);
            return;
        }

        boolean expired = match.getEndTime() != null && new Date().after(match.getEndTime());
        int idx = match.getCurIdx();

        if (match.getProblems() == null || idx >= match.getProblems().size()) {
            finishMatch(match.getId(), null);
            return;
        }

        String problem = match.getProblems().get(idx);

        log.debug("Checking Problem: {}", problem);

        // Even when the deadline has just passed, still credit a solve that was submitted before it:
        // the scheduler's poll interval must not decide the result. Solves after the deadline are
        // rejected inside checkSolve.
        SolveResult result;
        try {
            result = checkSolve(
                    match.getPlayer1CFHandle(),
                    match.getPlayer2CFHandle(),
                    problem,
                    match
            );
        } catch (CodeforcesService.CodeforcesUnavailableException e) {
            // Unknown is not "unsolved": never decide a result from an outage.
            log.warn("Codeforces unavailable while checking match {}: {}", match.getInviteCode(), e.getMessage());
            if (expired && match.getEndTime() != null
                    && System.currentTimeMillis() < match.getEndTime().getTime() + CF_OUTAGE_GRACE_MS) {
                return; // keep the match open and retry next tick
            }
            result = null;
        }

        if (result == null) {
            log.debug("No one solved yet");
        } else {
            String winner = result.getWinner();
            log.debug("Winner: {}", winner);

            // 🔥 ATOMIC UPDATE
            boolean success = tryUpdateMatch(
                    match.getId(),
                    idx,
                    winner,
                    match
            );

            if (success) {
                log.debug("✅ Winner locked: {}", winner);
                // 📡 Fetch latest and publish via WebSocket
                matchRepository.findById(match.getId()).ifPresent(this::publishMatchUpdate);
            } else {
                log.debug("❌ Race lost");
            }
        }

        if (expired) {
            finishMatch(match.getId(), null);
        }
    }

    /**
     * Decides who solved the current problem first. Only accepted submissions inside [startTime, endTime] count.
     * If both solved, the earlier submission wins; if they have the identical second, the lower Codeforces
     * submission id (= the one Codeforces received first) wins, so the outcome is deterministic.
     */
    private SolveResult checkSolve(String user1, String user2, String problem, MatchPrimary match) {

        CodeforcesService.SolveInfo s1 = withinDeadline(codeforcesService.fetchSolve(user1, problem, match.getStartTime()), match);
        CodeforcesService.SolveInfo s2 = withinDeadline(codeforcesService.fetchSolve(user2, problem, match.getStartTime()), match);

        if (s1 == null && s2 == null) return null;

        if (s1 != null && s2 == null) return new SolveResult(user1, s1.time());
        if (s1 == null) return new SolveResult(user2, s2.time());

        int byTime = s1.time().compareTo(s2.time());
        boolean firstWins = byTime != 0 ? byTime < 0 : s1.submissionId() <= s2.submissionId();
        return firstWins ? new SolveResult(user1, s1.time()) : new SolveResult(user2, s2.time());
    }

    private CodeforcesService.SolveInfo withinDeadline(CodeforcesService.SolveInfo solve, MatchPrimary match) {
        if (solve == null) return null;
        if (match.getEndTime() != null && solve.time().after(match.getEndTime())) return null;
        return solve;
    }

    // 🔥 ATOMIC UPDATE (prevents race conditions)
    private boolean tryUpdateMatch(String matchId, int expectedIdx, String winner, MatchPrimary match) {

        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(matchId));
        query.addCriteria(Criteria.where("curIdx").is(expectedIdx));
        // never score a match that is already being closed
        query.addCriteria(Criteria.where("status").is("ONGOING"));

        Update update = new Update();

        String p1 = match.getPlayer1CFHandle();

        if (winner.equalsIgnoreCase(p1) || winner.equalsIgnoreCase(match.getUser1())) {
            update.inc("score1", 1);
            update.set("player1Results." + expectedIdx, "SOLVED");
            update.set("player2Results." + expectedIdx, "—");
        } else {
            update.inc("score2", 1);
            update.set("player2Results." + expectedIdx, "SOLVED");
            update.set("player1Results." + expectedIdx, "—");
        }

        update.inc("curIdx", 1);
        // a skip vote only ever applies to the problem it was cast on
        update.set("skipVoteUser1", false);
        update.set("skipVoteUser2", false);

        UpdateResult result = mongoTemplate.updateFirst(query, update, MatchPrimary.class);

        return result.getModifiedCount() > 0;
    }

    private MatchSecondary finishMatch(String matchId, String reason) {
        MatchPrimary frozen = freezeMatch(matchId, reason);
        if (frozen == null) return null;
        return archiveMatch(frozen);
    }

    /**
     * Atomically moves ONGOING -> FINISHING so that exactly one caller (scheduler or creator-stop)
     * closes a match, and no further scoring can happen. If the match is already FINISHING (a previous
     * attempt crashed or is still running) the frozen document is returned so the archive can be completed;
     * archiveMatch is idempotent. Returns null if there is nothing to finish.
     */
    private MatchPrimary freezeMatch(String matchId, String reason) {
        Query query = new Query(Criteria.where("_id").is(matchId).and("status").is("ONGOING"));
        Update update = new Update().set("status", "FINISHING");
        if (reason != null) {
            update.set("endedReason", reason);
        }
        MatchPrimary frozen = mongoTemplate.findAndModify(
                query, update, FindAndModifyOptions.options().returnNew(true), MatchPrimary.class);
        if (frozen != null) return frozen;

        MatchPrimary existing = matchRepository.findById(matchId).orElse(null);
        return (existing != null && "FINISHING".equals(existing.getStatus())) ? existing : null;
    }

    /** Writes the final result exactly once (unique inviteCode on secondary_matches) and removes the live match. */
    private MatchSecondary archiveMatch(MatchPrimary match) {

        log.debug("🏁 Match Finished: {}", match.getId());

        String winner;
        if (match.getScore1() > match.getScore2()) {
            winner = match.getPlayer1CFHandle();
        } else if (match.getScore2() > match.getScore1()) {
            winner = match.getPlayer2CFHandle();
        } else {
            winner = "DRAW";
        }

        String endedReason = match.getEndedReason();
        if (endedReason == null || endedReason.isBlank()) {
            endedReason = (match.getProblems() != null && match.getCurIdx() >= match.getProblems().size())
                    ? "ALL_PROBLEMS_SOLVED" : "TIME_EXPIRED";
        }

        // 🔥 CREATE SECONDARY ENTRY
        MatchSecondary newMatch = new MatchSecondary();

        newMatch.setUser1(match.getUser1());
        newMatch.setUser2(match.getUser2());
        newMatch.setPlayer1CFHandle(match.getPlayer1CFHandle());
        newMatch.setPlayer2CFHandle(match.getPlayer2CFHandle());
        newMatch.setPlayer1Id(match.getPlayer1Id());
        newMatch.setPlayer2Id(match.getPlayer2Id());
        newMatch.setDurationMinutes(match.getDurationMinutes());
        newMatch.setDifficulty(match.getDifficulty());

        newMatch.setScore1(match.getScore1());
        newMatch.setScore2(match.getScore2());

        newMatch.setProblems(match.getProblems());
        newMatch.setCurIdx(match.getCurIdx());

        newMatch.setStatus("FINISHED");
        newMatch.setEndedReason(endedReason);
        newMatch.setWinnerId(winner);

        newMatch.setStartTime(match.getStartTime());
        newMatch.setEndTime(new Date());

        newMatch.setInviteCode(match.getInviteCode());
        newMatch.setPlayer1Results(match.getPlayer1Results());
        newMatch.setPlayer2Results(match.getPlayer2Results());
        newMatch.setTournamentId(match.getTournamentId());
        newMatch.setBracketRound(match.getBracketRound() != null ? match.getBracketRound() : 0);

        // save to secondary — the unique inviteCode index makes this the single "winner takes the result" point
        MatchSecondary saved;
        boolean firstArchive = true;
        try {
            saved = matchRepository2.save(newMatch);
        } catch (DuplicateKeyException e) {
            log.info("Match {} was already archived; skipping duplicate result processing", match.getInviteCode());
            firstArchive = false;
            saved = matchRepository2.findByInviteCode(match.getInviteCode()).orElse(newMatch);
        }

        // Hook for tournament advancement if applicable (only once per match)
        if (firstArchive && match.getTournamentId() != null) {
            tournamentService.onMatchFinished(match.getTournamentId(), match.getInviteCode(), winner);
        }

        // delete from primary so the scheduler stops polling it
        matchRepository.deleteById(match.getId());

        match.setStatus("FINISHED");
        match.setWinnerId(saved.getWinnerId());
        match.setEndedReason(saved.getEndedReason());
        publishMatchUpdate(match);

        return saved;
    }

    public String generateCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder code = new StringBuilder();

        for (int i = 0; i < 6; i++) {
            SecureRandom random = new SecureRandom();
            int idx = random.nextInt(chars.length());
            code.append(chars.charAt(idx));
        }

        return code.toString();
    }

    private void validateVerifiedHandle(String cfHandle) {
        if (cfHandle == null || cfHandle.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Codeforces account not connected.");
        }
        User user = userRepository.findByCodeforcesHandleIgnoreCase(cfHandle)
                .orElseGet(() -> userRepository.findByCfHandleIgnoreCase(cfHandle)
                .orElseGet(() -> userRepository.findByEmail(cfHandle).orElse(null)));

        if (user == null || !user.isCodeforcesVerified()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Cannot participate in match. Codeforces account for '" + cfHandle + "' is not verified.");
        }
    }

    private String resolveVerifiedCfHandle(String... candidates) {
        for (String cand : candidates) {
            if (cand != null && !cand.isBlank()) {
                User user = userRepository.findByCodeforcesHandleIgnoreCase(cand)
                        .orElseGet(() -> userRepository.findByCfHandleIgnoreCase(cand)
                        .orElseGet(() -> userRepository.findByEmail(cand).orElse(null)));
                if (user != null && user.isCodeforcesVerified() && user.getCfHandle() != null && !user.getCfHandle().isBlank()) {
                    return user.getCfHandle();
                }
            }
        }
        for (String cand : candidates) {
            if (cand != null && !cand.isBlank()) return cand;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Codeforces account not connected or verified.");
    }

    // ✅ Create Match
    public MatchSecondary createMatch(String cfHandle, int durationMinutes, String difficulty) {
        validateVerifiedHandle(cfHandle);

        MatchSecondary match = new MatchSecondary();

        match.setUser1(cfHandle);
        match.setPlayer1CFHandle(cfHandle);
        match.setDurationMinutes(durationMinutes);
        match.setScore1(0);
        match.setScore2(0);
        match.setCurIdx(0);

        match.setStatus("WAITING");
        match.setInviteCode(generateCode());
        match.setDifficulty(difficulty); 

        match.setStartTime(null);
        match.setEndTime(null);

        // store duration temporarily in endTime later
        match.setEndTime(new Date(durationMinutes * 60 * 1000L)); // temp storage trick

        return matchRepository2.save(match);
    }

    public MatchSecondary joinMatch(String cfHandle, String inviteCode) {
        validateVerifiedHandle(cfHandle);
        String code = normalizeCode(inviteCode);

        MatchSecondary match = matchRepository2.findByInviteCode(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid invite code"));

        // Refresh / double-click / retry by the player who already holds the second seat: succeed without changes.
        if (match.getUser2() != null && cfHandle.equalsIgnoreCase(match.getUser2())
                && ("READY".equals(match.getStatus()) || "WAITING".equals(match.getStatus()))) {
            return match;
        }

        if (cfHandle.equalsIgnoreCase(match.getUser1()) || cfHandle.equalsIgnoreCase(match.getPlayer1CFHandle())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot join your own match");
        }

        if (match.getUser2() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Match already full");
        }

        if (!"WAITING".equals(match.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Match is not open for joining");
        }

        if (lobbyExpired(match)) {
            throw new ResponseStatusException(HttpStatus.GONE, "This lobby has expired. Ask the host to create a new duel.");
        }

        // 🔥 Atomic claim of the open seat: of two simultaneous joiners exactly one matches this filter,
        // so a lobby can never be silently overwritten by the second request.
        Query query = new Query(Criteria.where("_id").is(match.getId())
                .and("status").is("WAITING")
                .and("user2").is(null));
        Update update = new Update()
                .set("user2", cfHandle)
                .set("player2CFHandle", cfHandle)
                .set("status", "READY");

        MatchSecondary joined = mongoTemplate.findAndModify(
                query, update, FindAndModifyOptions.options().returnNew(true), MatchSecondary.class);

        if (joined == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Match was just taken or is no longer open");
        }
        // tell the host immediately (only after the change is persisted)
        messagingTemplate.convertAndSend("/topic/match/" + joined.getInviteCode(), joined);
        return joined;
    }


    public MatchPrimary startMatch(String cfHandle, String rawInviteCode) {
        String inviteCode = normalizeCode(rawInviteCode);

        MatchSecondary secondary = matchRepository2.findByInviteCode(inviteCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid code"));

        User requesterUser = userRepository.findByCodeforcesHandleIgnoreCase(cfHandle)
                .orElseGet(() -> userRepository.findByCfHandleIgnoreCase(cfHandle)
                .orElseGet(() -> userRepository.findByEmail(cfHandle).orElse(null)));

        String reqEmail = requesterUser != null ? requesterUser.getEmail() : cfHandle;
        String reqCfHandle = requesterUser != null ? requesterUser.getCfHandle() : cfHandle;

        boolean isCreator = (reqCfHandle != null && (reqCfHandle.equalsIgnoreCase(secondary.getUser1()) || reqCfHandle.equalsIgnoreCase(secondary.getPlayer1CFHandle())))
                || (reqEmail != null && (reqEmail.equalsIgnoreCase(secondary.getUser1()) || reqEmail.equalsIgnoreCase(secondary.getPlayer1CFHandle())));

        if (!isCreator) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only creator can start");
        }

        if (!"READY".equals(secondary.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Player 2 not joined/ready");
        }

        if (lobbyExpired(secondary)) {
            throw new ResponseStatusException(HttpStatus.GONE, "This lobby has expired. Create a new duel.");
        }

        // ✅ REQUIREMENT: Both players must have verified Codeforces identities
        String p1Handle = resolveVerifiedCfHandle(secondary.getPlayer1CFHandle(), secondary.getUser1());
        String p2Handle = resolveVerifiedCfHandle(secondary.getPlayer2CFHandle(), secondary.getUser2());

        validateVerifiedHandle(p1Handle);
        validateVerifiedHandle(p2Handle);

        // Pick the problems BEFORE the clock starts, so slow Codeforces calls do not eat into the match
        // time, and so a failure leaves the lobby untouched and retryable instead of starting a broken match.
        List<String> problemUrls;
        try {
            problemUrls = problemService.getMatchProblems(p1Handle, p2Handle, secondary.getDifficulty());
        } catch (Exception e) {
            log.warn("Failed to fetch problems for match {}: {}", secondary.getInviteCode(), e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Could not select problems right now. Please try starting again.");
        }
        if (problemUrls == null || problemUrls.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Could not select problems right now. Please try starting again.");
        }

        Date startTime;
        Date endTime;
        if (secondary.getTournamentId() != null) {
            CF_DuelProject.CF_DuelProject.model.Tournament t = tournamentRepository.findById(secondary.getTournamentId()).orElse(null);
            if (t != null && t.getRoundStartTime() != null && t.getRoundEndTime() != null) {
                startTime = t.getRoundStartTime();
                endTime = t.getRoundEndTime();
            } else if (secondary.getStartTime() != null && secondary.getEndTime() != null && secondary.getEndTime().after(secondary.getStartTime())) {
                startTime = secondary.getStartTime();
                endTime = secondary.getEndTime();
            } else {
                startTime = new Date();
                long durationMillis = (secondary.getDurationMinutes() > 0 ? secondary.getDurationMinutes() : 30) * 60 * 1000L;
                endTime = new Date(startTime.getTime() + durationMillis);
            }
        } else {
            startTime = new Date();
            long durationMillis = secondary.getEndTime() != null && secondary.getEndTime().getTime() < 100000000000L
                    ? secondary.getEndTime().getTime()
                    : (secondary.getDurationMinutes() > 0 ? (secondary.getDurationMinutes() * 60 * 1000L) : 30 * 60 * 1000L);
            endTime = new Date(startTime.getTime() + durationMillis);
        }

        MatchPrimary primary = new MatchPrimary();

        primary.setUser1(p1Handle);
        primary.setUser2(p2Handle);
        primary.setPlayer1CFHandle(p1Handle);
        primary.setPlayer2CFHandle(p2Handle);
        primary.setPlayer1Id(secondary.getPlayer1Id());
        primary.setPlayer2Id(secondary.getPlayer2Id());
        primary.setTournamentId(secondary.getTournamentId());
        primary.setBracketRound(secondary.getBracketRound());
        primary.setDurationMinutes(secondary.getDurationMinutes() > 0 ? secondary.getDurationMinutes() : (int) ((endTime.getTime() - startTime.getTime()) / (60 * 1000)));

        // Transfer difficulty from lobby to live match
        primary.setDifficulty(secondary.getDifficulty());

        primary.setScore1(0);
        primary.setScore2(0);
        primary.setCurIdx(0);

        primary.setStatus("ONGOING");
        primary.setWinnerId(null);

        primary.setStartTime(startTime);
        primary.setEndTime(endTime);
        primary.setInviteCode(secondary.getInviteCode());
        primary.setProblems(problemUrls);

        // 🔥 Atomic claim of the lobby: only one concurrent /start can remove it, so a duel can never
        // be started twice (the other request gets 409 instead of a second problem set / 500).
        Query claim = new Query(Criteria.where("_id").is(secondary.getId()).and("status").is("READY"));
        MatchSecondary claimed = mongoTemplate.findAndRemove(claim, MatchSecondary.class);
        if (claimed == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Match has already been started");
        }

        MatchPrimary saved;
        try {
            saved = matchRepository.save(primary);
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Match has already been started");
        } catch (RuntimeException e) {
            // put the lobby back so the creator can retry
            matchRepository2.save(claimed);
            throw e;
        }
        publishMatchUpdate(saved);

        return saved;
    }

    public MatchPrimary startTournamentMatch(Tournament t, String rawInviteCode) {
        String inviteCode = normalizeCode(rawInviteCode);

        MatchSecondary secondary = matchRepository2.findByInviteCode(inviteCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid code"));

        if (!"READY".equals(secondary.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Player 2 not joined/ready");
        }

        String p1Handle = resolveVerifiedCfHandle(secondary.getPlayer1CFHandle(), secondary.getUser1());
        String p2Handle = resolveVerifiedCfHandle(secondary.getPlayer2CFHandle(), secondary.getUser2());

        validateVerifiedHandle(p1Handle);
        validateVerifiedHandle(p2Handle);

        List<String> problemUrls;
        try {
            problemUrls = problemService.getMatchProblems(p1Handle, p2Handle, secondary.getDifficulty());
        } catch (Exception e) {
            log.warn("Failed to fetch problems for tournament match {}: {}", secondary.getInviteCode(), e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Could not select problems right now. Please try starting again.");
        }
        if (problemUrls == null || problemUrls.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Could not select problems right now. Please try starting again.");
        }

        Date startTime;
        Date endTime;
        if (t != null && t.getRoundStartTime() != null && t.getRoundEndTime() != null) {
            startTime = t.getRoundStartTime();
            endTime = t.getRoundEndTime();
        } else if (secondary.getTournamentId() != null) {
            Tournament foundT = tournamentRepository.findById(secondary.getTournamentId()).orElse(null);
            if (foundT != null && foundT.getRoundStartTime() != null && foundT.getRoundEndTime() != null) {
                startTime = foundT.getRoundStartTime();
                endTime = foundT.getRoundEndTime();
            } else {
                startTime = new Date();
                long durationMillis = (secondary.getDurationMinutes() > 0 ? secondary.getDurationMinutes() : 30) * 60 * 1000L;
                endTime = new Date(startTime.getTime() + durationMillis);
            }
        } else {
            startTime = new Date();
            long durationMillis = (secondary.getDurationMinutes() > 0 ? secondary.getDurationMinutes() : 30) * 60 * 1000L;
            endTime = new Date(startTime.getTime() + durationMillis);
        }

        MatchPrimary primary = new MatchPrimary();
        primary.setUser1(p1Handle);
        primary.setUser2(p2Handle);
        primary.setPlayer1CFHandle(p1Handle);
        primary.setPlayer2CFHandle(p2Handle);

        String id1 = secondary.getPlayer1Id();
        if (id1 == null) {
            id1 = userRepository.findByCodeforcesHandleIgnoreCase(p1Handle)
                    .or(() -> userRepository.findByCfHandleIgnoreCase(p1Handle))
                    .map(User::getId).orElse(null);
        }
        String id2 = secondary.getPlayer2Id();
        if (id2 == null) {
            id2 = userRepository.findByCodeforcesHandleIgnoreCase(p2Handle)
                    .or(() -> userRepository.findByCfHandleIgnoreCase(p2Handle))
                    .map(User::getId).orElse(null);
        }
        primary.setPlayer1Id(id1);
        primary.setPlayer2Id(id2);
        primary.setTournamentId(t != null ? t.getId() : secondary.getTournamentId());
        primary.setBracketRound(secondary.getBracketRound());
        primary.setDurationMinutes(secondary.getDurationMinutes() > 0 ? secondary.getDurationMinutes() : (int) ((endTime.getTime() - startTime.getTime()) / (60 * 1000)));
        primary.setDifficulty(secondary.getDifficulty());
        primary.setScore1(0);
        primary.setScore2(0);
        primary.setCurIdx(0);
        primary.setStatus("ONGOING");
        primary.setWinnerId(null);
        primary.setStartTime(startTime);
        primary.setEndTime(endTime);
        primary.setInviteCode(secondary.getInviteCode());
        primary.setProblems(problemUrls);

        Query claim = new Query(Criteria.where("_id").is(secondary.getId()).and("status").is("READY"));
        MatchSecondary claimed = mongoTemplate.findAndRemove(claim, MatchSecondary.class);
        if (claimed == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Match has already been started");
        }

        MatchPrimary saved;
        try {
            saved = matchRepository.save(primary);
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Match has already been started");
        } catch (RuntimeException e) {
            matchRepository2.save(claimed);
            throw e;
        }
        publishMatchUpdate(saved);
        return saved;
    }

    public Map<String, Object> getMatchStatus(String inviteCode) {
        String normalizedCode = normalizeCode(inviteCode);

        Optional<MatchPrimary> primary = matchRepository.findByInviteCode(normalizedCode);
        if (primary.isPresent()) {
            MatchPrimary m = primary.get();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("source", "PRIMARY");
            payload.put("id", m.getId());
            payload.put("inviteCode", m.getInviteCode());
            payload.put("status", m.getStatus());
            payload.put("endedReason", m.getEndedReason());
            payload.put("user1", m.getUser1());
            payload.put("user2", m.getUser2());
            payload.put("player1CFHandle", m.getPlayer1CFHandle());
            payload.put("player2CFHandle", m.getPlayer2CFHandle());
            payload.put("score1", m.getScore1());
            payload.put("score2", m.getScore2());
            payload.put("curIdx", m.getCurIdx());
            payload.put("winnerId", m.getWinnerId());
            payload.put("problems", m.getProblems());
            payload.put("startTime", m.getStartTime());
            payload.put("endTime", m.getEndTime());
            payload.put("player1Results", m.getPlayer1Results());
            payload.put("player2Results", m.getPlayer2Results());
            payload.put("skipVoteUser1", m.isSkipVoteUser1());
            payload.put("skipVoteUser2", m.isSkipVoteUser2());
            payload.put("tournamentId", m.getTournamentId());
            payload.put("bracketRound", m.getBracketRound());
            // authoritative server clock so clients can correct their local clock skew for the countdown
            payload.put("serverTime", new Date());
            return payload;
        }

        Optional<MatchSecondary> secondary = matchRepository2.findByInviteCode(normalizedCode);
        if (secondary.isPresent()) {
            MatchSecondary m = secondary.get();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("source", "SECONDARY");
            payload.put("id", m.getId());
            payload.put("inviteCode", m.getInviteCode());
            payload.put("status", m.getStatus());
            payload.put("endedReason", m.getEndedReason());
            payload.put("user1", m.getUser1());
            payload.put("user2", m.getUser2());
            payload.put("player1CFHandle", m.getPlayer1CFHandle());
            payload.put("player2CFHandle", m.getPlayer2CFHandle());
            payload.put("score1", m.getScore1());
            payload.put("score2", m.getScore2());
            payload.put("curIdx", m.getCurIdx());
            payload.put("winnerId", m.getWinnerId());
            payload.put("problems", m.getProblems());
            payload.put("startTime", m.getStartTime());
            payload.put("endTime", m.getEndTime());
            payload.put("player1Results", m.getPlayer1Results());
            payload.put("player2Results", m.getPlayer2Results());
            payload.put("tournamentId", m.getTournamentId());
            payload.put("bracketRound", m.getBracketRound());
            // authoritative server clock so clients can correct their local clock skew for the countdown
            payload.put("serverTime", new Date());
       
            return payload;
        }

        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Match not found for invite code");
    }
    
    public List<MatchSecondary> getUserMatchHistory(String userId) {
        if (userId == null || userId.isBlank()) {
            return List.of();
        }

        java.util.Set<String> queryKeys = new java.util.HashSet<>();
        queryKeys.add(userId.trim());

        User user = userRepository.findByEmail(userId)
                .orElseGet(() -> userRepository.findByCodeforcesHandleIgnoreCase(userId)
                .orElseGet(() -> userRepository.findByCfHandleIgnoreCase(userId)
                .orElseGet(() -> userRepository.findById(userId).orElse(null))));

        if (user != null) {
            if (user.getEmail() != null) queryKeys.add(user.getEmail().trim());
            if (user.getCfHandle() != null) queryKeys.add(user.getCfHandle().trim());
            if (user.getCodeforcesHandle() != null) queryKeys.add(user.getCodeforcesHandle().trim());
            if (user.getId() != null) queryKeys.add(user.getId().trim());
        }

        // Filter in the database (case-insensitive exact match) instead of loading every match into memory.
        String[] fields = {"user1", "user2", "player1CFHandle", "player2CFHandle", "player1Id", "player2Id"};
        List<Criteria> anyField = new ArrayList<>();
        for (String key : queryKeys) {
            if (key.isEmpty()) continue;
            java.util.regex.Pattern exact = java.util.regex.Pattern.compile(
                    "^" + java.util.regex.Pattern.quote(key) + "$", java.util.regex.Pattern.CASE_INSENSITIVE);
            for (String field : fields) {
                anyField.add(Criteria.where(field).regex(exact));
            }
        }
        if (anyField.isEmpty()) {
            return List.of();
        }

        Query query = new Query(new Criteria().andOperator(
                Criteria.where("status").is("FINISHED"),
                new Criteria().orOperator(anyField.toArray(new Criteria[0]))));

        return mongoTemplate.find(query, MatchSecondary.class).stream()
                .sorted((a, b) -> {
                    Date d1 = a.getEndTime() != null ? a.getEndTime() : (a.getStartTime() != null ? a.getStartTime() : new Date(0));
                    Date d2 = b.getEndTime() != null ? b.getEndTime() : (b.getStartTime() != null ? b.getStartTime() : new Date(0));
                    return d2.compareTo(d1);
                })
                .toList();
    }

    /** True if {@code identifier} (email, CF handle or id) refers to the same account as {@code requesterEmail}. */
    public boolean isSameAccount(String requesterEmail, String identifier) {
        if (requesterEmail == null || identifier == null) return false;
        String wanted = identifier.trim();
        if (wanted.equalsIgnoreCase(requesterEmail.trim())) return true;
        User me = userRepository.findByEmail(requesterEmail).orElse(null);
        if (me == null) return false;
        return wanted.equalsIgnoreCase(me.getCfHandle())
                || wanted.equalsIgnoreCase(me.getCodeforcesHandle())
                || wanted.equals(me.getId());
    }

    /**
     * Returns the caller's Codeforces handle if (and only if) they are one of the two players of the match
     * with this invite code; null otherwise. Used to authorize and attribute WebSocket and REST messages server-side.
     */
    public String resolveParticipantHandle(String email, String inviteCode) {
        if (email == null || inviteCode == null || inviteCode.isBlank()) return null;
        String ident = inviteCode.trim();
        String code = normalizeCode(ident);
        MatchPrimary primary = matchRepository.findByInviteCode(code)
                .orElseGet(() -> matchRepository.findById(ident).orElse(null));
        MatchSecondary secondary = primary == null ? matchRepository2.findByInviteCode(code)
                .orElseGet(() -> matchRepository2.findById(ident).orElse(null)) : null;

        String trimmed = email.trim();
        User user = userRepository.findByEmail(trimmed)
                .orElseGet(() -> userRepository.findByEmail(trimmed.toLowerCase())
                .orElseGet(() -> userRepository.findById(trimmed)
                .orElseGet(() -> userRepository.findByCfHandleIgnoreCase(trimmed)
                .orElseGet(() -> userRepository.findByCodeforcesHandleIgnoreCase(trimmed)
                .orElse(null)))));

        if (primary != null || secondary != null) {
            String p1 = primary != null ? primary.getPlayer1CFHandle() : secondary.getPlayer1CFHandle();
            String p2 = primary != null ? primary.getPlayer2CFHandle() : secondary.getPlayer2CFHandle();
            String u1 = primary != null ? primary.getUser1() : secondary.getUser1();
            String u2 = primary != null ? primary.getUser2() : secondary.getUser2();
            String id1 = primary != null ? primary.getPlayer1Id() : secondary.getPlayer1Id();
            String id2 = primary != null ? primary.getPlayer2Id() : secondary.getPlayer2Id();
            String tournamentId = primary != null ? primary.getTournamentId() : secondary.getTournamentId();

            if (user != null) {
                String cf = user.getCfHandle();
                String uid = user.getId();
                String uEmail = user.getEmail();
                if (matchesPlayer(p1, u1, id1, cf, uid, uEmail, trimmed)) {
                    return (cf != null && !cf.isBlank()) ? cf : (p1 != null ? p1 : (u1 != null ? u1 : trimmed));
                }
                if (matchesPlayer(p2, u2, id2, cf, uid, uEmail, trimmed)) {
                    return (cf != null && !cf.isBlank()) ? cf : (p2 != null ? p2 : (u2 != null ? u2 : trimmed));
                }
            } else {
                if (matchesDirect(p1, u1, id1, trimmed)) {
                    return p1 != null ? p1 : (u1 != null ? u1 : trimmed);
                }
                if (matchesDirect(p2, u2, id2, trimmed)) {
                    return p2 != null ? p2 : (u2 != null ? u2 : trimmed);
                }
            }

            // Tournament referee/host and admin can also chat in tournament matches
            if (tournamentId != null) {
                Tournament t = tournamentRepository.findById(tournamentId).orElse(null);
                if (t != null) {
                    boolean isHost = (t.getCreatorEmail() != null && t.getCreatorEmail().equalsIgnoreCase(trimmed))
                            || (user != null && user.getCfHandle() != null && t.getCreatorId() != null && t.getCreatorId().equalsIgnoreCase(user.getCfHandle()))
                            || (t.getCreatorId() != null && t.getCreatorId().equalsIgnoreCase(trimmed))
                            || (user != null && t.getCreatorEmail() != null && t.getCreatorEmail().equalsIgnoreCase(user.getEmail()));
                    if (isHost) {
                        return (user != null && user.getCfHandle() != null && !user.getCfHandle().isBlank())
                                ? user.getCfHandle() : (t.getCreatorId() != null ? t.getCreatorId() : trimmed);
                    }
                    if (t.getRegisteredPlayers() != null) {
                        String cf = user != null ? user.getCfHandle() : trimmed;
                        for (String p : t.getRegisteredPlayers()) {
                            if (p != null && (p.equalsIgnoreCase(cf) || p.equalsIgnoreCase(trimmed))) {
                                return (user != null && user.getCfHandle() != null && !user.getCfHandle().isBlank()) ? user.getCfHandle() : p;
                            }
                        }
                    }
                }
            }

            if (user != null && user.isAdmin()) {
                return (user.getCfHandle() != null && !user.getCfHandle().isBlank()) ? user.getCfHandle() : trimmed;
            }

            return null;
        }

        // Tournament room chat resolution (when identifier is a Tournament ID or invite code)
        Tournament tourney = tournamentRepository.findById(ident)
                .orElseGet(() -> tournamentRepository.findByInviteCode(code)
                .orElseGet(() -> tournamentRepository.findById(code).orElse(null)));

        if (tourney != null) {
            String cf = user != null ? user.getCfHandle() : trimmed;
            boolean isHost = (tourney.getCreatorEmail() != null && tourney.getCreatorEmail().equalsIgnoreCase(trimmed))
                    || (tourney.getCreatorId() != null && tourney.getCreatorId().equalsIgnoreCase(cf))
                    || (tourney.getCreatorId() != null && tourney.getCreatorId().equalsIgnoreCase(trimmed))
                    || (user != null && tourney.getCreatorEmail() != null && tourney.getCreatorEmail().equalsIgnoreCase(user.getEmail()));
            if (isHost) {
                return (user != null && user.getCfHandle() != null && !user.getCfHandle().isBlank())
                        ? user.getCfHandle() : (tourney.getCreatorId() != null ? tourney.getCreatorId() : trimmed);
            }
            if (tourney.getRegisteredPlayers() != null) {
                for (String p : tourney.getRegisteredPlayers()) {
                    if (p != null && (p.equalsIgnoreCase(cf) || p.equalsIgnoreCase(trimmed))) {
                        return (user != null && user.getCfHandle() != null && !user.getCfHandle().isBlank()) ? user.getCfHandle() : p;
                    }
                }
            }
            if (tourney.getJoinRequests() != null) {
                for (String p : tourney.getJoinRequests()) {
                    if (p != null && (p.equalsIgnoreCase(cf) || p.equalsIgnoreCase(trimmed))) {
                        return (user != null && user.getCfHandle() != null && !user.getCfHandle().isBlank()) ? user.getCfHandle() : p;
                    }
                }
            }
            if (user != null && user.isAdmin()) {
                return (user.getCfHandle() != null && !user.getCfHandle().isBlank()) ? user.getCfHandle() : trimmed;
            }
        }

        return null;
    }

    private boolean matchesPlayer(String pHandle, String uHandle, String pId, String userCf, String userId, String userEmail, String rawCaller) {
        if (userCf != null && !userCf.isBlank()) {
            if (userCf.equalsIgnoreCase(pHandle) || userCf.equalsIgnoreCase(uHandle)) return true;
        }
        if (userId != null && !userId.isBlank()) {
            if (userId.equalsIgnoreCase(pId) || userId.equalsIgnoreCase(uHandle)) return true;
        }
        if (userEmail != null && !userEmail.isBlank()) {
            if (userEmail.equalsIgnoreCase(uHandle) || userEmail.equalsIgnoreCase(pHandle)) return true;
        }
        return matchesDirect(pHandle, uHandle, pId, rawCaller);
    }

    private boolean matchesDirect(String pHandle, String uHandle, String pId, String rawCaller) {
        if (rawCaller == null || rawCaller.isBlank()) return false;
        return rawCaller.equalsIgnoreCase(pHandle) || rawCaller.equalsIgnoreCase(uHandle) || rawCaller.equalsIgnoreCase(pId);
    }

    private boolean isTournamentOrganizer(String tournamentId, java.util.Set<String> aliases) {
        return tournamentRepository.findById(tournamentId)
                .map(tt -> tt.getCreatorId() != null && aliases.contains(tt.getCreatorId().trim().toLowerCase()))
                .orElse(false);
    }

    /**
     * May this account see the live state/events of the match identified by invite code OR match id?
     * Allowed: the two players, the organizer of the tournament the match belongs to, and admins.
     */
    public boolean canViewMatch(String email, String identifier) {
        if (email == null || identifier == null || identifier.isBlank()) return false;
        String trimmed = email.trim();
        User user = userRepository.findByEmail(trimmed)
                .orElseGet(() -> userRepository.findByEmail(trimmed.toLowerCase())
                .orElseGet(() -> userRepository.findById(trimmed)
                .orElseGet(() -> userRepository.findByCfHandleIgnoreCase(trimmed)
                .orElseGet(() -> userRepository.findByCodeforcesHandleIgnoreCase(trimmed)
                .orElse(null)))));
        if (user != null && user.isAdmin()) return true;

        String ident = identifier.trim();
        String code = normalizeCode(ident);
        String p1; String p2; String u1; String u2; String id1; String id2; String tournamentId;
        MatchPrimary primary = matchRepository.findByInviteCode(code)
                .orElseGet(() -> matchRepository.findById(ident).orElse(null));
        if (primary != null) {
            p1 = primary.getPlayer1CFHandle(); p2 = primary.getPlayer2CFHandle();
            u1 = primary.getUser1(); u2 = primary.getUser2();
            id1 = primary.getPlayer1Id(); id2 = primary.getPlayer2Id();
            tournamentId = primary.getTournamentId();
        } else {
            MatchSecondary secondary = matchRepository2.findByInviteCode(code)
                    .orElseGet(() -> matchRepository2.findById(ident).orElse(null));
            if (secondary == null) {
                Tournament t = tournamentRepository.findById(ident)
                        .orElseGet(() -> tournamentRepository.findByInviteCode(code).orElse(null));
                if (t != null) {
                    if (t.getCreatorEmail() != null && t.getCreatorEmail().equalsIgnoreCase(trimmed)) return true;
                    if (user != null && user.getCfHandle() != null && t.getCreatorId() != null && t.getCreatorId().equalsIgnoreCase(user.getCfHandle())) return true;
                    if (t.getCreatorId() != null && t.getCreatorId().equalsIgnoreCase(trimmed)) return true;
                    if (t.getRegisteredPlayers() != null) {
                        String userCf = user != null ? user.getCfHandle() : trimmed;
                        for (String p : t.getRegisteredPlayers()) {
                            if (p != null && (p.equalsIgnoreCase(userCf) || p.equalsIgnoreCase(trimmed))) return true;
                        }
                    }
                    if (t.getJoinRequests() != null) {
                        String userCf = user != null ? user.getCfHandle() : trimmed;
                        for (String p : t.getJoinRequests()) {
                            if (p != null && (p.equalsIgnoreCase(userCf) || p.equalsIgnoreCase(trimmed))) return true;
                        }
                    }
                }
                return false;
            }
            p1 = secondary.getPlayer1CFHandle(); p2 = secondary.getPlayer2CFHandle();
            u1 = secondary.getUser1(); u2 = secondary.getUser2();
            id1 = secondary.getPlayer1Id(); id2 = secondary.getPlayer2Id();
            tournamentId = secondary.getTournamentId();
        }

        if (user != null) {
            String cf = user.getCfHandle();
            String uid = user.getId();
            String uEmail = user.getEmail();
            if (matchesPlayer(p1, u1, id1, cf, uid, uEmail, trimmed) || matchesPlayer(p2, u2, id2, cf, uid, uEmail, trimmed)) {
                return true;
            }
        } else {
            if (matchesDirect(p1, u1, id1, trimmed) || matchesDirect(p2, u2, id2, trimmed)) {
                return true;
            }
        }

        if (tournamentId != null) {
            CF_DuelProject.CF_DuelProject.model.Tournament t = tournamentRepository.findById(tournamentId).orElse(null);
            if (t != null) {
                if (t.getCreatorEmail() != null && t.getCreatorEmail().equalsIgnoreCase(trimmed)) return true;
                if (user != null && user.getCfHandle() != null && t.getCreatorId() != null && t.getCreatorId().equalsIgnoreCase(user.getCfHandle())) return true;
                if (t.getCreatorId() != null && t.getCreatorId().equalsIgnoreCase(trimmed)) return true;
            }
        }
        return false;
    }

    /** Status of a match, only for people allowed to see it. */
    public Map<String, Object> getMatchStatusFor(String inviteCode, String email) {
        Map<String, Object> status = getMatchStatus(inviteCode); // 404 if unknown
        if (!canViewMatch(email, inviteCode)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a participant of this match.");
        }
        return status;
    }

    private boolean isPlayer(String handle, String p1, String p2) {
        return (handle != null && p1 != null && handle.equalsIgnoreCase(p1))
                || (handle != null && p2 != null && handle.equalsIgnoreCase(p2));
    }

    public MatchSecondary stopMatchByCreator(String identifier, String requester) {
        if (identifier == null || identifier.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Match identifier cannot be blank");
        }
        String normalized = identifier.trim();

        // Find match in Primary or Secondary
        MatchPrimary primary = matchRepository.findByInviteCode(normalizeCode(normalized))
                .orElseGet(() -> matchRepository.findById(normalized).orElse(null));

        MatchSecondary secondary = null;
        if (primary == null) {
            secondary = matchRepository2.findByInviteCode(normalizeCode(normalized))
                    .orElseGet(() -> matchRepository2.findById(normalized).orElse(null));
        }

        if (primary == null && secondary == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Match not found: " + identifier);
        }

        // Collect requester aliases (email, handle, id)
        java.util.Set<String> reqAliases = new java.util.HashSet<>();
        reqAliases.add(requester.trim().toLowerCase());

        User reqUser = userRepository.findByEmail(requester)
                .orElseGet(() -> userRepository.findByCodeforcesHandleIgnoreCase(requester)
                .orElseGet(() -> userRepository.findByCfHandleIgnoreCase(requester)
                .orElseGet(() -> userRepository.findById(requester).orElse(null))));

        if (reqUser != null) {
            if (reqUser.getEmail() != null) reqAliases.add(reqUser.getEmail().trim().toLowerCase());
            if (reqUser.getCfHandle() != null) reqAliases.add(reqUser.getCfHandle().trim().toLowerCase());
            if (reqUser.getCodeforcesHandle() != null) reqAliases.add(reqUser.getCodeforcesHandle().trim().toLowerCase());
            if (reqUser.getId() != null) reqAliases.add(reqUser.getId().trim().toLowerCase());
        }

        if (primary != null) {
            if ("FINISHED".equals(primary.getStatus()) || "FINISHING".equals(primary.getStatus())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Match is already finished.");
            }

            // Check if requester is creator (user1 / player1CFHandle / player1Id)
            boolean isCreator = (primary.getUser1() != null && reqAliases.contains(primary.getUser1().trim().toLowerCase()))
                    || (primary.getPlayer1CFHandle() != null && reqAliases.contains(primary.getPlayer1CFHandle().trim().toLowerCase()))
                    || (primary.getPlayer1Id() != null && reqAliases.contains(primary.getPlayer1Id().trim().toLowerCase()));

            // Tournament matches are decided by results, not by a player: only the organizer may stop them.
            if (primary.getTournamentId() != null) {
                isCreator = isTournamentOrganizer(primary.getTournamentId(), reqAliases);
            }

            if (!isCreator) {
                log.warn("Unauthorized stop match attempt by '{}' for match {}", requester, primary.getId());
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the match creator can stop the match.");
            }

            log.info("🛑 Match stopped by creator '{}': {}", requester, primary.getId());

            // Same atomic close path as the scheduler: if the deadline/finish raced us, exactly one wins.
            MatchPrimary frozen = freezeMatch(primary.getId(), "CREATOR_STOPPED");
            if (frozen == null || !"CREATOR_STOPPED".equals(frozen.getEndedReason())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Match is already finished.");
            }
            MatchSecondary saved = archiveMatch(frozen);

            Map<String, Object> stoppedPayload = new LinkedHashMap<>();
            stoppedPayload.put("type", "MATCH_STOPPED");
            stoppedPayload.put("inviteCode", primary.getInviteCode());
            stoppedPayload.put("matchId", primary.getId());
            stoppedPayload.put("endedReason", "CREATOR_STOPPED");
            stoppedPayload.put("winnerId", saved.getWinnerId());
            stoppedPayload.put("stoppedBy", requester);

            messagingTemplate.convertAndSend("/topic/match/" + primary.getInviteCode() + "/stopped", stoppedPayload);
            if (primary.getId() != null) {
                messagingTemplate.convertAndSend("/topic/match/" + primary.getId() + "/stopped", stoppedPayload);
            }

            return saved;
        } else {
            // Secondary match check
            boolean isCreator = (secondary.getUser1() != null && reqAliases.contains(secondary.getUser1().trim().toLowerCase()))
                    || (secondary.getPlayer1CFHandle() != null && reqAliases.contains(secondary.getPlayer1CFHandle().trim().toLowerCase()))
                    || (secondary.getPlayer1Id() != null && reqAliases.contains(secondary.getPlayer1Id().trim().toLowerCase()));

            if (secondary.getTournamentId() != null) {
                isCreator = isTournamentOrganizer(secondary.getTournamentId(), reqAliases);
            }

            if (!isCreator) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the match creator can stop the match.");
            }

            if ("FINISHED".equals(secondary.getStatus())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Match is already finished.");
            }

            // Conditional update (not a whole-document save) so a concurrent join/start is never clobbered.
            Query lobby = new Query(Criteria.where("_id").is(secondary.getId())
                    .and("status").in("WAITING", "READY"));
            Update cancel = new Update()
                    .set("status", "FINISHED")
                    .set("endedReason", "CREATOR_STOPPED")
                    .set("endTime", new Date());
            if (secondary.getWinnerId() == null || secondary.getWinnerId().isBlank()) {
                cancel.set("winnerId", "CANCELLED");
            }
            MatchSecondary stopped = mongoTemplate.findAndModify(
                    lobby, cancel, FindAndModifyOptions.options().returnNew(true), MatchSecondary.class);
            if (stopped == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Match can no longer be stopped (it was just started or finished).");
            }
            secondary = stopped;

            Map<String, Object> stoppedPayload = new LinkedHashMap<>();
            stoppedPayload.put("type", "MATCH_STOPPED");
            stoppedPayload.put("inviteCode", secondary.getInviteCode());
            stoppedPayload.put("matchId", secondary.getId());
            stoppedPayload.put("endedReason", "CREATOR_STOPPED");
            stoppedPayload.put("winnerId", secondary.getWinnerId());
            stoppedPayload.put("stoppedBy", requester);

            messagingTemplate.convertAndSend("/topic/match/" + secondary.getInviteCode() + "/stopped", stoppedPayload);
            messagingTemplate.convertAndSend("/topic/match/" + secondary.getInviteCode(), secondary);

            return secondary;
        }
    }


    // Safe version of getMatchStatus — returns null instead of throwing (used by TournamentScheduler)
    public Map<String, Object> getMatchStatusSafe(String inviteCode) {
        try {
            return getMatchStatus(inviteCode);
        } catch (Exception e) {
            return null;
        }
    }

    public void handleSkipVote(String inviteCode, String cfHandle) {
        if (inviteCode == null || cfHandle == null) return;

        MatchPrimary match = matchRepository.findByInviteCode(normalizeCode(inviteCode)).orElse(null);
        if (match == null) {
            log.debug("Skip vote ignored — match not found: {}", inviteCode);
            return;
        }

        if (!"ONGOING".equals(match.getStatus())) {
            log.debug("Skip vote ignored — match is not ONGOING");
            return;
        }

        int idx = match.getCurIdx();
        if (match.getProblems() == null || idx >= match.getProblems().size()) {
            log.debug("Skip vote ignored — no more problems");
            return;
        }

        // Determine which player is voting
        boolean isUser1 = cfHandle.equalsIgnoreCase(match.getPlayer1CFHandle())
                || cfHandle.equalsIgnoreCase(match.getUser1())
                || cfHandle.equalsIgnoreCase(match.getPlayer1Id());
        boolean isUser2 = cfHandle.equalsIgnoreCase(match.getPlayer2CFHandle())
                || cfHandle.equalsIgnoreCase(match.getUser2())
                || cfHandle.equalsIgnoreCase(match.getPlayer2Id());

        if (!isUser1 && !isUser2) {
            String trimmed = cfHandle.trim();
            User user = userRepository.findByEmail(trimmed)
                    .orElseGet(() -> userRepository.findByEmail(trimmed.toLowerCase())
                    .orElseGet(() -> userRepository.findById(trimmed)
                    .orElseGet(() -> userRepository.findByCfHandleIgnoreCase(trimmed)
                    .orElseGet(() -> userRepository.findByCodeforcesHandleIgnoreCase(trimmed)
                    .orElse(null)))));
            if (user != null) {
                String h = user.getCfHandle();
                String id = user.getId();
                String em = user.getEmail();
                if (matchesPlayer(match.getPlayer1CFHandle(), match.getUser1(), match.getPlayer1Id(), h, id, em, trimmed)) {
                    isUser1 = true;
                } else if (matchesPlayer(match.getPlayer2CFHandle(), match.getUser2(), match.getPlayer2Id(), h, id, em, trimmed)) {
                    isUser2 = true;
                }
            }
        }

        if (!isUser1 && !isUser2) {
            log.debug("Skip vote ignored — sender is not a player in this match: {}", cfHandle);
            return;
        }

        // All writes below are field-level and conditional on the problem index, so they can neither
        // lose a concurrent score update nor apply a vote to a different problem than the one it was cast on.
        Query onThisProblem = new Query(Criteria.where("_id").is(match.getId())
                .and("status").is("ONGOING")
                .and("curIdx").is(idx));
        Update vote = new Update().set(isUser1 ? "skipVoteUser1" : "skipVoteUser2", true);
        if (mongoTemplate.updateFirst(onThisProblem, vote, MatchPrimary.class).getMatchedCount() == 0) {
            log.debug("Skip vote ignored — problem already advanced");
            return;
        }

        // Advance only if BOTH votes are present; the filter makes this happen at most once per problem.
        Query bothVoted = new Query(Criteria.where("_id").is(match.getId())
                .and("status").is("ONGOING")
                .and("curIdx").is(idx)
                .and("skipVoteUser1").is(true)
                .and("skipVoteUser2").is(true));
        Update advance = new Update()
                .set("player1Results." + idx, "SKIPPED")
                .set("player2Results." + idx, "SKIPPED")
                .inc("curIdx", 1)
                .set("skipVoteUser1", false)
                .set("skipVoteUser2", false);

        if (mongoTemplate.updateFirst(bothVoted, advance, MatchPrimary.class).getModifiedCount() > 0) {
            log.debug("⏭ Both players voted to skip problem {}", idx);
            matchRepository.findById(match.getId()).ifPresent(this::publishMatchUpdate);
        } else {
            // Only one player voted so far — broadcast so the opponent sees the vote
            log.debug("⏳ {} voted to skip. Waiting for opponent...", cfHandle);
            MatchPrimary fresh = matchRepository.findById(match.getId()).orElse(match);

            Map<String, Object> skipNotification = new LinkedHashMap<>();
            skipNotification.put("inviteCode", match.getInviteCode());
            skipNotification.put("voter", cfHandle);
            skipNotification.put("skipVoteUser1", fresh.isSkipVoteUser1());
            skipNotification.put("skipVoteUser2", fresh.isSkipVoteUser2());
            messagingTemplate.convertAndSend("/topic/match/" + match.getInviteCode() + "/skip", skipNotification);
            publishMatchUpdate(fresh);
        }
    }

    public void sendChatMessage(String inviteCode, String senderEmailOrId, String text) {
        String handle = resolveParticipantHandle(senderEmailOrId, inviteCode);
        if (handle == null || text == null || text.isBlank()) return;
        String cleaned = text.strip();
        if (cleaned.length() > 500) cleaned = cleaned.substring(0, 500);

        ChatMessage msg = new ChatMessage();
        msg.setInviteCode(normalizeCode(inviteCode));
        msg.setSender(handle);
        msg.setText(cleaned);

        String norm = normalizeCode(inviteCode);
        messagingTemplate.convertAndSend(
            "/topic/match/" + norm + "/chat",
            msg
        );
        if (tournamentService != null && tournamentService.isTournament(inviteCode)) {
            messagingTemplate.convertAndSend("/topic/tournament/" + inviteCode + "/chat", msg);
            messagingTemplate.convertAndSend("/topic/tournament/" + norm + "/chat", msg);
        }
    }

    public void sendEmoteMessage(String inviteCode, String senderEmailOrId, String emote) {
        String handle = resolveParticipantHandle(senderEmailOrId, inviteCode);
        if (handle == null || emote == null || emote.isBlank()) return;
        String cleaned = emote.strip();
        if (cleaned.length() > 32) cleaned = cleaned.substring(0, 32);

        EmoteMessage msg = new EmoteMessage();
        msg.setInviteCode(normalizeCode(inviteCode));
        msg.setSender(handle);
        msg.setEmote(cleaned);

        String norm = normalizeCode(inviteCode);
        messagingTemplate.convertAndSend(
            "/topic/match/" + norm + "/emotes",
            msg
        );
        if (tournamentService != null && tournamentService.isTournament(inviteCode)) {
            messagingTemplate.convertAndSend("/topic/tournament/" + inviteCode + "/emotes", msg);
            messagingTemplate.convertAndSend("/topic/tournament/" + norm + "/emotes", msg);
        }
    }
}
