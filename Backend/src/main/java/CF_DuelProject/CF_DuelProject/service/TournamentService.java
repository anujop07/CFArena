package CF_DuelProject.CF_DuelProject.service;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import CF_DuelProject.CF_DuelProject.model.MatchSecondary;
import CF_DuelProject.CF_DuelProject.model.Tournament;
import CF_DuelProject.CF_DuelProject.repository.SecondaryMatchRepository;
import CF_DuelProject.CF_DuelProject.repository.TournamentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class TournamentService {

    private final TournamentRepository tournamentRepository;
    private final CF_DuelProject.CF_DuelProject.repository.PrimaryMatchRepository matchRepository;
    private final SecondaryMatchRepository matchRepository2;
    private final SimpMessagingTemplate messagingTemplate;

    // ─── Code Generator (reuses same pattern as MatchService) ───
    private String generateCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            int idx = (int) (Math.random() * chars.length());
            code.append(chars.charAt(idx));
        }
        return code.toString();
    }

    // ─── Publish tournament bracket update via WebSocket ───
    private void publishTournamentUpdate(Tournament t) {
        log.debug("📡 Tournament WS → /topic/tournament/{}", t.getId());
        messagingTemplate.convertAndSend("/topic/tournament/" + t.getId(), t);
    }

    // ═══════════════════════════════════════════════════════════
    // CREATE TOURNAMENT
    // ═══════════════════════════════════════════════════════════
    public Tournament createTournament(String creatorCfHandle, String name, int maxPlayers,
            String difficulty, int matchDurationMinutes) {

        // Validate maxPlayers is power of 2
        if (maxPlayers != 8 && maxPlayers != 16 && maxPlayers != 32) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "maxPlayers must be 8, 16, or 32");
        }

        int totalRounds = (int) (Math.log(maxPlayers) / Math.log(2));

        Tournament t = new Tournament();
        t.setName(name);
        t.setCreatorId(creatorCfHandle);
        t.setDifficulty(difficulty);
        t.setMaxPlayers(maxPlayers);
        t.setMatchDurationMinutes(matchDurationMinutes);
        t.setStatus("REGISTRATION");
        t.setInviteCode(generateCode());
        t.setCreatedAt(new Date());
        t.setTotalRounds(totalRounds);
        t.setCurrentRound(0);

        // Auto-register creator
        List<String> players = new ArrayList<>();
        players.add(creatorCfHandle);
        t.setRegisteredPlayers(players);

        return tournamentRepository.save(t);
    }

    // ═══════════════════════════════════════════════════════════
    // REGISTER PLAYER
    // ═══════════════════════════════════════════════════════════
    public Tournament registerPlayer(String tournamentId, String cfHandle) {
        Tournament t = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tournament not found"));

        if (!"REGISTRATION".equals(t.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Registration is closed");
        }

        if (t.getRegisteredPlayers().contains(cfHandle)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Already registered");
        }

        if (t.getRegisteredPlayers().size() >= t.getMaxPlayers()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tournament is full");
        }

        t.getRegisteredPlayers().add(cfHandle);
        Tournament saved = tournamentRepository.save(t);
        publishTournamentUpdate(saved);
        return saved;
    }

    // ═══════════════════════════════════════════════════════════
    // UNREGISTER PLAYER
    // ═══════════════════════════════════════════════════════════
    public Tournament unregisterPlayer(String tournamentId, String cfHandle) {
        Tournament t = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tournament not found"));

        if (!"REGISTRATION".equals(t.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot unregister after tournament starts");
        }

        if (cfHandle.equals(t.getCreatorId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Creator cannot unregister");
        }

        t.getRegisteredPlayers().remove(cfHandle);
        Tournament saved = tournamentRepository.save(t);
        publishTournamentUpdate(saved);
        return saved;
    }

    // ═══════════════════════════════════════════════════════════
    // START ROUND MATCHES MANUALLY (Creator only)
    // ═══════════════════════════════════════════════════════════
    public Tournament startRoundMatchesManual(String tournamentId, String cfHandle, int round, MatchService matchService) {
        Tournament t = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tournament not found"));

        if (!cfHandle.equals(t.getCreatorId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the creator can start rounds");
        }

        if (!"ONGOING".equals(t.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tournament is not ongoing");
        }

        if (t.getCurrentRound() != round) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Can only start the current active round");
        }

        // Use the existing logic to start the matches
        startRoundMatches(t, matchService);

        return t;
    }

    // ═══════════════════════════════════════════════════════════
    // START TOURNAMENT (Creator only)
    // ═══════════════════════════════════════════════════════════
    public Tournament startTournament(String tournamentId, String cfHandle) {
        Tournament t = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tournament not found"));

        if (!cfHandle.equals(t.getCreatorId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only creator can start the tournament");
        }

        if (!"REGISTRATION".equals(t.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tournament already started or finished");
        }

        int playerCount = t.getRegisteredPlayers().size();
        if (playerCount < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Need at least 2 players");
        }

        // Shuffle players & pad with BYEs
        List<String> players = new ArrayList<>(t.getRegisteredPlayers());
        Collections.shuffle(players);

        while (players.size() < t.getMaxPlayers()) {
            players.add("BYE");
        }

        // Set Round 1 bracket
        Map<Integer, List<String>> bracket = new HashMap<>();
        bracket.put(1, players);
        t.setBracket(bracket);

        // Create Round 1 matches
        List<String> round1Codes = createRoundMatches(t, 1, players);
        Map<Integer, List<String>> matchCodes = new HashMap<>();
        matchCodes.put(1, round1Codes);
        t.setMatchCodes(matchCodes);

        t.setStatus("ONGOING");
        t.setCurrentRound(1);

        Tournament saved = tournamentRepository.save(t);
        publishTournamentUpdate(saved);

        log.info("🏆 Tournament started: {} with {} players", t.getName(), playerCount);
        
        // If round 1 is magically all BYEs (e.g. only 1 player registered)
        boolean allByes = true;
        for (String c : round1Codes) {
            if (!"BYE".equals(c)) {
                allByes = false;
                break;
            }
        }
        if (allByes) {
            log.info("⏩ Round 1 is all BYEs. Auto-advancing!");
            advanceRound(saved);
        }

        return saved;
    }

    // ═══════════════════════════════════════════════════════════
    // CREATE ROUND MATCHES
    // Creates MatchSecondary lobbies for each pair in the round
    // ═══════════════════════════════════════════════════════════
    private List<String> createRoundMatches(Tournament t, int roundNumber, List<String> roundPlayers) {
        List<String> codes = new ArrayList<>();

        for (int i = 0; i < roundPlayers.size(); i += 2) {
            String player1 = roundPlayers.get(i);
            String player2 = (i + 1 < roundPlayers.size()) ? roundPlayers.get(i + 1) : "BYE";

            if ("BYE".equals(player1) || "BYE".equals(player2)) {
                // Auto-advance — no real match needed
                codes.add("BYE");
                continue;
            }

            // Create a MatchSecondary lobby (reuses existing model)
            MatchSecondary lobby = new MatchSecondary();
            lobby.setUser1(player1);
            lobby.setUser2(player2);
            lobby.setStatus("READY"); // Both players pre-set
            lobby.setDifficulty(t.getDifficulty());
            lobby.setInviteCode(generateCode());
            lobby.setScore1(0);
            lobby.setScore2(0);
            lobby.setCurIdx(0);

            // Duration stored same way as existing createMatch()
            lobby.setEndTime(new Date(t.getMatchDurationMinutes() * 60 * 1000L));
            lobby.setStartTime(null);

            // Tournament link
            lobby.setTournamentId(t.getId());
            lobby.setBracketRound(roundNumber);

            matchRepository2.save(lobby);
            codes.add(lobby.getInviteCode());

            log.debug("  📋 Match created: {} vs {} [{}]", player1, player2, lobby.getInviteCode());
        }

        return codes;
    }

    // ═══════════════════════════════════════════════════════════
    // START ROUND MATCHES
    // Called by TournamentScheduler when round time arrives.
    // Promotes MatchSecondary → MatchPrimary using existing startMatch flow.
    // ═══════════════════════════════════════════════════════════
    public void startRoundMatches(Tournament t, MatchService matchService) {
        int round = t.getCurrentRound();
        List<String> codes = t.getMatchCodes().get(round);
        if (codes == null) return;

        for (String code : codes) {
            if ("BYE".equals(code)) continue;

            try {
                // Find the lobby
                MatchSecondary lobby = matchRepository2.findByInviteCode(code).orElse(null);
                if (lobby == null || !"READY".equals(lobby.getStatus())) continue;

                // Use existing startMatch logic — player1 starts the match
                matchService.startMatch(lobby.getUser1(), code);
                log.debug("  🚀 Round {} match started: {}", round, code);
            } catch (Exception e) {
                log.error("  ❌ Failed to start match {}: {}", code, e.getMessage());
            }
        }

        publishTournamentUpdate(t);
    }

    // ═══════════════════════════════════════════════════════════
    // ON MATCH FINISHED (Hook from MatchService)
    // ═══════════════════════════════════════════════════════════
    public void onMatchFinished(String tournamentId, String inviteCode, String winnerId) {
        int retries = 3;
        while (retries > 0) {
            try {
                processMatchFinished(tournamentId, inviteCode, winnerId);
                break;
            } catch (org.springframework.dao.OptimisticLockingFailureException e) {
                retries--;
                if (retries == 0) {
                    log.error("❌ Failed to update tournament after retries due to concurrent modifications.");
                } else {
                    try { Thread.sleep(100); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                }
            }
        }
    }

    private void processMatchFinished(String tournamentId, String inviteCode, String winnerId) {
        Tournament t = tournamentRepository.findById(tournamentId).orElse(null);
        if (t == null || !"ONGOING".equals(t.getStatus())) return;

        int round = t.getCurrentRound();
        List<String> codes = t.getMatchCodes().get(round);
        if (codes == null) return;

        log.info("🏆 Tournament match finished: {} → winner: {}", inviteCode, winnerId);

        // Check if ALL matches in current round are finished
        boolean allFinished = true;
        for (String code : codes) {
            if ("BYE".equals(code)) continue;

            MatchSecondary match = matchRepository2.findByInviteCode(code).orElse(null);
            if (match == null) {
                log.warn("  ❌ match is null for code: {}", code);
                allFinished = false;
                break;
            } else if (!"FINISHED".equals(match.getStatus())) {
                log.debug("  ❌ match status is not FINISHED: {} for code: {}", match.getStatus(), code);
                allFinished = false;
                break;
            } else {
                log.debug("  ✅ match is FINISHED for code: {}", code);
            }
        }

        if (allFinished) {
            log.info("✅ All matches in round {} finished. Waiting for admin to advance.", round);
            // REMOVED auto-advance: advanceRound(t);
        }

        publishTournamentUpdate(t);
    }

    public Tournament advanceRoundManual(String tournamentId, String cfHandle) {
        Tournament t = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tournament not found"));

        if (!cfHandle.equals(t.getCreatorId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the creator can advance the round");
        }

        if (!"ONGOING".equals(t.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tournament is not ongoing");
        }

        int round = t.getCurrentRound();
        List<String> codes = t.getMatchCodes().get(round);
        
        if (codes != null) {
            for (String code : codes) {
                if ("BYE".equals(code)) continue;

                MatchSecondary match = matchRepository2.findByInviteCode(code).orElse(null);
                if (match == null || !"FINISHED".equals(match.getStatus())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not all matches are finished in the current round");
                }
            }
        }

        advanceRound(t);
        publishTournamentUpdate(t);
        return t;
    }

    // ═══════════════════════════════════════════════════════════
    // ADVANCE ROUND — THE CORE ALGORITHM
    // Collects winners from current round → builds next round
    // ═══════════════════════════════════════════════════════════
    private void advanceRound(Tournament t) {
        int currentRound = t.getCurrentRound();
        List<String> currentBracket = t.getBracket().get(currentRound);
        List<String> codes = t.getMatchCodes().get(currentRound);

        List<String> nextBracket = new ArrayList<>();

        // Each pair (2i, 2i+1) had a match. Collect winners.
        int matchIdx = 0;
        for (int i = 0; i < currentBracket.size(); i += 2) {
            String player1 = currentBracket.get(i);
            String player2 = (i + 1 < currentBracket.size()) ? currentBracket.get(i + 1) : "BYE";

            String winner;

            if ("BYE".equals(player1)) {
                winner = player2;
            } else if ("BYE".equals(player2)) {
                winner = player1;
            } else {
                // Look up the match result
                String code = (matchIdx < codes.size()) ? codes.get(matchIdx) : null;
                if (code != null && !"BYE".equals(code)) {
                    // If it's a finished match, it will be in Secondary with FINISHED status
                    MatchSecondary match = matchRepository2.findByInviteCode(code).orElse(null);
                    if (match != null && match.getWinnerId() != null) {
                        winner = match.getWinnerId();
                        if ("DRAW".equals(winner)) {
                            java.security.SecureRandom random = new java.security.SecureRandom();
                            winner = random.nextBoolean() ? player1 : player2;
                            log.info("Match {} ended in DRAW, randomly advancing {}", code, winner);
                        }
                    } else {
                        winner = player1; // fallback
                    }
                } else {
                    winner = player1; // fallback
                }
            }

            nextBracket.add(winner);
            matchIdx++;
        }

        int nextRound = currentRound + 1;

        // Set next round bracket
        t.getBracket().put(nextRound, nextBracket);
        t.setCurrentRound(nextRound);

        if (nextBracket.size() == 1) {
            // 🏆 Tournament over!
            t.setWinnerId(nextBracket.get(0));
            t.setStatus("FINISHED");
            log.info("🏆🏆🏆 Tournament FINISHED! Winner: {}", nextBracket.get(0));
        } else {
            // Create matches for next round
            List<String> nextCodes = createRoundMatches(t, nextRound, nextBracket);
            t.getMatchCodes().put(nextRound, nextCodes);
            log.info("➡️ Advanced to round {} with {} players", nextRound, nextBracket.size());
            
            // Check if this new round is entirely BYEs
            boolean allByes = true;
            for (String c : nextCodes) {
                if (!"BYE".equals(c)) {
                    allByes = false;
                    break;
                }
            }

            if (allByes) {
                log.info("⏩ Round {} is all BYEs. Auto-advancing!", nextRound);
                advanceRound(t);
                return; // The recursive call will handle saving
            }
        }

        tournamentRepository.save(t);
    }

    // ═══════════════════════════════════════════════════════════
    // QUERY METHODS
    // ═══════════════════════════════════════════════════════════

    public Tournament getTournamentById(String id) {
        return tournamentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tournament not found"));
    }

    public List<Tournament> getActiveTournaments() {
        return tournamentRepository.findByStatusIn(List.of("REGISTRATION", "ONGOING"));
    }



    public MatchSecondary getSecondaryMatch(String inviteCode) {
        return matchRepository2.findByInviteCode(inviteCode).orElse(null);
    }

    public List<Tournament> getMyTournaments(String cfHandle) {
        return tournamentRepository.findByRegisteredPlayersContaining(cfHandle);
    }

    // Get match data for a specific tournament round (for bracket display)
    public List<Map<String, Object>> getRoundMatchDetails(String tournamentId, int roundNumber) {
        Tournament t = getTournamentById(tournamentId);
        List<String> codes = t.getMatchCodes().get(roundNumber);
        List<String> bracket = t.getBracket().get(roundNumber);
        if (codes == null || bracket == null) return List.of();

        List<Map<String, Object>> matches = new ArrayList<>();
        int matchIdx = 0;

        for (int i = 0; i < bracket.size(); i += 2) {
            Map<String, Object> matchInfo = new HashMap<>();
            String p1 = bracket.get(i);
            String p2 = (i + 1 < bracket.size()) ? bracket.get(i + 1) : "BYE";

            matchInfo.put("matchIndex", matchIdx);
            matchInfo.put("player1", p1);
            matchInfo.put("player2", p2);
            matchInfo.put("round", roundNumber);

            if (matchIdx < codes.size()) {
                String code = codes.get(matchIdx);
                matchInfo.put("inviteCode", code);

                if (!"BYE".equals(code)) {
                    CF_DuelProject.CF_DuelProject.model.MatchPrimary primaryMatch = matchRepository.findByInviteCode(code).orElse(null);
                    if (primaryMatch != null) {
                        matchInfo.put("status", primaryMatch.getStatus());
                        matchInfo.put("score1", primaryMatch.getScore1());
                        matchInfo.put("score2", primaryMatch.getScore2());
                        matchInfo.put("winnerId", primaryMatch.getWinnerId());
                    } else {
                        MatchSecondary match = matchRepository2.findByInviteCode(code).orElse(null);
                        if (match != null) {
                            matchInfo.put("status", match.getStatus());
                            matchInfo.put("score1", match.getScore1());
                            matchInfo.put("score2", match.getScore2());
                            matchInfo.put("winnerId", match.getWinnerId());
                        } else {
                            matchInfo.put("status", "SCHEDULED");
                        }
                    }
                } else {
                    matchInfo.put("status", "BYE");
                    matchInfo.put("winnerId", "BYE".equals(p1) ? p2 : p1);
                }
            }

            matches.add(matchInfo);
            matchIdx++;
        }

        return matches;
    }

    // Full bracket with match details for all rounds
    public Map<String, Object> getFullBracket(String tournamentId) {
        Tournament t = getTournamentById(tournamentId);
        Map<String, Object> result = new HashMap<>();
        result.put("tournament", t);

        Map<Integer, List<Map<String, Object>>> allRounds = new HashMap<>();
        for (int round = 1; round <= t.getTotalRounds(); round++) {
            if (t.getBracket().containsKey(round)) {
                allRounds.put(round, getRoundMatchDetails(tournamentId, round));
            }
        }
        result.put("rounds", allRounds);

        return result;
    }
}
