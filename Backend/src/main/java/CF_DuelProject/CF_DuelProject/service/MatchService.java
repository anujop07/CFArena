package CF_DuelProject.CF_DuelProject.service;

import java.util.Date;
import java.util.List;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.mongodb.client.result.UpdateResult;

import CF_DuelProject.CF_DuelProject.dto.SolveResult;
import CF_DuelProject.CF_DuelProject.model.MatchPrimary;
import CF_DuelProject.CF_DuelProject.model.MatchSecondary;
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
    private final MongoTemplate mongoTemplate;
    private final CodeforcesService codeforcesService;
    private final ProblemService problemService;
    private final SimpMessagingTemplate messagingTemplate;


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

        // ⏱ check match end
        if (new Date().after(match.getEndTime())) {
            finishMatch(match.getId());
            return;
        }

        int idx = match.getCurIdx();

        if (idx >= match.getProblems().size()) {
            finishMatch(match.getId());
            return;
        }

        String problem = match.getProblems().get(idx);

        log.debug("Checking Problem: {}", problem);

        SolveResult result = checkSolve(
                match.getUser1(),
                match.getUser2(),
                problem,
                match
        );

        if (result == null) {
            log.debug("No one solved yet");
            return;
        }

        String winner = result.getWinner();
        log.debug("Winner: {}", winner);

        // 🔥 ATOMIC UPDATE
        boolean success = tryUpdateMatch(
                match.getId(),
                match.getCurIdx(),
                winner,
                match
        );

        if (success) {
            log.debug("✅ Winner locked: {}", winner);
            // 📡 Fetch latest and publish via WebSocket
            MatchPrimary latest = matchRepository.findById(match.getId()).orElse(null);
            if (latest != null) {
                publishMatchUpdate(latest);
            }
        } else {
            log.debug("❌ Race lost");
        }
    }

    private SolveResult checkSolve(String user1, String user2, String problem, MatchPrimary match) {

        Date t1 = codeforcesService.getSolveTime(user1, problem, match.getStartTime());
        Date t2 = codeforcesService.getSolveTime(user2, problem, match.getStartTime());

        if (t1 == null && t2 == null) return null;

        if (t1 != null && t2 == null) return new SolveResult(user1, t1);
        if (t1 == null && t2 != null) return new SolveResult(user2, t2);

        return t1.before(t2)
                ? new SolveResult(user1, t1)
                : new SolveResult(user2, t2);
    }

    // 🔥 ATOMIC UPDATE (prevents race conditions)
    private boolean tryUpdateMatch(String matchId, int expectedIdx, String winner, MatchPrimary match) {

        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(matchId));
        query.addCriteria(Criteria.where("curIdx").is(expectedIdx));

        Update update = new Update();

        if (winner.equals(match.getUser1())) {
            update.inc("score1", 1);
            update.set("player1Results." + expectedIdx, "SOLVED");
            update.set("player2Results." + expectedIdx, "—");
        } else {
            update.inc("score2", 1);
            update.set("player2Results." + expectedIdx, "SOLVED");
            update.set("player1Results." + expectedIdx, "—");
        }

        update.inc("curIdx", 1);

        UpdateResult result = mongoTemplate.updateFirst(query, update, MatchPrimary.class);

        return result.getModifiedCount() > 0;
    }

    private void finishMatch(String matchId) {

        MatchPrimary match = matchRepository.findById(matchId).orElse(null);
        if (match == null) return;

        if ("FINISHED".equals(match.getStatus())) return;

        log.debug("🏁 Match Finished: {}", match.getId());

        String winner;
        if (match.getScore1() > match.getScore2()) {
            winner = match.getUser1();
        } else if (match.getScore2() > match.getScore1()) {
            winner = match.getUser2();
        } else {
            winner = "DRAW";
        }

        // 🔥 CREATE SECONDARY ENTRY
        MatchSecondary newMatch = new MatchSecondary();

        newMatch.setUser1(match.getUser1());
        newMatch.setUser2(match.getUser2());

        newMatch.setScore1(match.getScore1());
        newMatch.setScore2(match.getScore2());

        newMatch.setProblems(match.getProblems());
        newMatch.setCurIdx(match.getCurIdx());

        newMatch.setStatus("FINISHED");
        newMatch.setWinnerId(winner);

        newMatch.setStartTime(match.getStartTime());
        newMatch.setEndTime(match.getEndTime());

        newMatch.setInviteCode(match.getInviteCode());
        newMatch.setPlayer1Results(match.getPlayer1Results());
    newMatch.setPlayer2Results(match.getPlayer2Results());
        // save to secondary
        MatchSecondary saved = matchRepository2.save(newMatch);

        // delete from primary
        
        publishMatchUpdate(match); 
        matchRepository.delete(match);
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

    // ✅ Create Match
    public MatchSecondary createMatch(String userId, int durationMinutes,String difficulty) {

        MatchSecondary match = new MatchSecondary();

        match.setUser1(userId);
        match.setScore1(0);
        match.setScore2(0);
        match.setCurIdx(0);

        match.setStatus("WAITING");
        match.setInviteCode(generateCode());
        match.setDifficulty(difficulty); 

        match.setStartTime(null);
        match.setEndTime(null);

        // store duration temporarily in endTime later
        match.setEndTime(new Date(durationMinutes * 60 * 1000)); // temp storage trick

        return matchRepository2.save(match);
    }

    public MatchSecondary joinMatch(String userId, String inviteCode) {

    MatchSecondary match = matchRepository2.findByInviteCode(inviteCode)
            .orElseThrow(() -> new RuntimeException("Invalid invite code"));

    if (match.getUser2() != null) {
        throw new RuntimeException("Match already full");
    }

    if (match.getUser1().equals(userId)) {
        throw new RuntimeException("Cannot join your own match");
    }

    match.setUser2(userId);

    // 🔥 IMPORTANT
    match.setStatus("READY");

    MatchSecondary saved = matchRepository2.save(match);
    // publishMatchUpdate(saved);

    return saved;
    }


    public MatchPrimary startMatch(String userId, String inviteCode) {

    MatchSecondary secondary = matchRepository2.findByInviteCode(inviteCode)
            .orElseThrow(() -> new RuntimeException("Invalid code"));

    if (!userId.equals(secondary.getUser1())) {
        throw new RuntimeException("Only creator can start");
    }

    if (!"READY".equals(secondary.getStatus())) {
        throw new RuntimeException("Player 2 not joined/ready");
    }

    Date startTime = new Date();

    long durationMillis = secondary.getEndTime().getTime();
    Date endTime = new Date(startTime.getTime() + durationMillis);

    MatchPrimary primary = new MatchPrimary();

    primary.setUser1(secondary.getUser1());
    primary.setUser2(secondary.getUser2());

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

    // Pass saved difficulty directly to problem engine
    List<String> problemUrls = problemService.getMatchProblems(
            secondary.getUser1(),
            secondary.getUser2(),
            secondary.getDifficulty()
    );
    primary.setProblems(problemUrls);

    MatchPrimary saved = matchRepository.save(primary);
    matchRepository2.delete(secondary);
    publishMatchUpdate(saved);

    return saved;
}
    public Map<String, Object> getMatchStatus(String inviteCode) {
        String normalizedCode = inviteCode == null ? "" : inviteCode.trim();

        Optional<MatchPrimary> primary = matchRepository.findByInviteCode(normalizedCode);
        if (primary.isPresent()) {
            MatchPrimary m = primary.get();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("source", "PRIMARY");
            payload.put("id", m.getId());
            payload.put("inviteCode", m.getInviteCode());
            payload.put("status", m.getStatus());
            payload.put("user1", m.getUser1());
            payload.put("user2", m.getUser2());
            payload.put("score1", m.getScore1());
            payload.put("score2", m.getScore2());
            payload.put("curIdx", m.getCurIdx());
            payload.put("winnerId", m.getWinnerId());
            payload.put("problems", m.getProblems());
            payload.put("startTime", m.getStartTime());
            payload.put("endTime", m.getEndTime());
            payload.put("player1Results", m.getPlayer1Results());
            payload.put("player2Results", m.getPlayer2Results());
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
            payload.put("user1", m.getUser1());
            payload.put("user2", m.getUser2());
            payload.put("score1", m.getScore1());
            payload.put("score2", m.getScore2());
            payload.put("curIdx", m.getCurIdx());
            payload.put("winnerId", m.getWinnerId());
            payload.put("problems", m.getProblems());
            payload.put("startTime", m.getStartTime());
            payload.put("endTime", m.getEndTime());
            payload.put("player1Results", m.getPlayer1Results());
            payload.put("player2Results", m.getPlayer2Results());
       
            return payload;
        }

        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Match not found for invite code");
    }
    
        public List<MatchSecondary> getUserMatchHistory(String userId) {
        
        List<MatchSecondary> allMatches = matchRepository2.findByUser1OrUser2(userId, userId);
        
        return allMatches.stream()
                .filter(match -> "FINISHED".equals(match.getStatus()))
                .toList();
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

        MatchPrimary match = matchRepository.findByInviteCode(inviteCode.trim())
                .orElseThrow(() -> new RuntimeException("Match not found: " + inviteCode));

        if (!"ONGOING".equals(match.getStatus())) {
            log.debug("Skip vote ignored — match is not ONGOING");
            return;
        }

        int idx = match.getCurIdx();
        if (idx >= match.getProblems().size()) {
            log.debug("Skip vote ignored — no more problems");
            return;
        }

        // Determine which player is voting
        boolean isUser1 = cfHandle.equalsIgnoreCase(match.getUser1());
        boolean isUser2 = cfHandle.equalsIgnoreCase(match.getUser2());

        if (!isUser1 && !isUser2) {
            log.debug("Skip vote ignored — sender is not a player in this match");
            return;
        }

        if (isUser1) match.setSkipVoteUser1(true);
        if (isUser2) match.setSkipVoteUser2(true);

        // Check if both players have voted
        if (match.isSkipVoteUser1() && match.isSkipVoteUser2()) {
            log.debug("⏭ Both players voted to skip problem {}", idx);

            // Mark both results as SKIPPED for this problem
            match.getPlayer1Results().put(idx, "SKIPPED");
            match.getPlayer2Results().put(idx, "SKIPPED");

            // Advance to next problem
            match.setCurIdx(idx + 1);

            // Reset skip votes for the next problem
            match.setSkipVoteUser1(false);
            match.setSkipVoteUser2(false);

            matchRepository.save(match);
            publishMatchUpdate(match);
        } else {
            // Only one player voted so far — save and broadcast so opponent sees the vote
            log.debug("⏳ {} voted to skip. Waiting for opponent...", cfHandle);
            matchRepository.save(match);

            // Broadcast updated skip vote state via the skip topic
            Map<String, Object> skipNotification = new LinkedHashMap<>();
            skipNotification.put("inviteCode", inviteCode);
            skipNotification.put("voter", cfHandle);
            skipNotification.put("skipVoteUser1", match.isSkipVoteUser1());
            skipNotification.put("skipVoteUser2", match.isSkipVoteUser2());
            messagingTemplate.convertAndSend("/topic/match/" + inviteCode + "/skip", skipNotification);
        }
    }
}
