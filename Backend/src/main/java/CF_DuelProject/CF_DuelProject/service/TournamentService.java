package CF_DuelProject.CF_DuelProject.service;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import java.security.SecureRandom;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import CF_DuelProject.CF_DuelProject.model.MatchSecondary;
import CF_DuelProject.CF_DuelProject.model.User;
import CF_DuelProject.CF_DuelProject.model.Tournament;
import CF_DuelProject.CF_DuelProject.repository.SecondaryMatchRepository;
import CF_DuelProject.CF_DuelProject.repository.TournamentRepository;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class TournamentService {

    private final TournamentRepository tournamentRepository;
    private final CF_DuelProject.CF_DuelProject.repository.PrimaryMatchRepository matchRepository;
    private final SecondaryMatchRepository matchRepository2;
    private final CF_DuelProject.CF_DuelProject.repository.UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final CodeforcesService codeforcesService;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public TournamentService(TournamentRepository tournamentRepository,
                             CF_DuelProject.CF_DuelProject.repository.PrimaryMatchRepository matchRepository,
                             SecondaryMatchRepository matchRepository2,
                             CF_DuelProject.CF_DuelProject.repository.UserRepository userRepository,
                             SimpMessagingTemplate messagingTemplate,
                             @Autowired(required = false) CodeforcesService codeforcesService) {
        this.tournamentRepository = tournamentRepository;
        this.matchRepository = matchRepository;
        this.matchRepository2 = matchRepository2;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
        this.codeforcesService = codeforcesService;
    }

    public TournamentService(TournamentRepository tournamentRepository,
                             CF_DuelProject.CF_DuelProject.repository.PrimaryMatchRepository matchRepository,
                             SecondaryMatchRepository matchRepository2,
                             CF_DuelProject.CF_DuelProject.repository.UserRepository userRepository,
                             SimpMessagingTemplate messagingTemplate) {
        this(tournamentRepository, matchRepository, matchRepository2, userRepository, messagingTemplate, null);
    }

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

    // ─── Host Authorization Helper ───
    public boolean isHost(Tournament t, String cfHandle, String email) {
        if (t == null) return false;
        if (cfHandle != null && !cfHandle.isBlank() && t.getCreatorId() != null
                && cfHandle.trim().equalsIgnoreCase(t.getCreatorId().trim())) {
            return true;
        }
        if (email != null && !email.isBlank() && t.getCreatorEmail() != null
                && email.trim().equalsIgnoreCase(t.getCreatorEmail().trim())) {
            return true;
        }
        return false;
    }

    public boolean isTournament(String identifier) {
        if (identifier == null || identifier.isBlank()) return false;
        String trimmed = identifier.trim();
        return tournamentRepository.findById(trimmed).isPresent()
                || tournamentRepository.findByInviteCode(trimmed).isPresent()
                || tournamentRepository.findByInviteCode(MatchService.normalizeCode(trimmed)).isPresent();
    }

    public boolean canParticipateInTournament(String email, String tournamentId) {
        if (email == null || tournamentId == null || tournamentId.isBlank()) return false;
        String trimmed = tournamentId.trim();
        Tournament t = tournamentRepository.findById(trimmed)
                .orElseGet(() -> tournamentRepository.findByInviteCode(trimmed)
                .orElseGet(() -> tournamentRepository.findByInviteCode(MatchService.normalizeCode(trimmed))
                .orElse(null)));
        if (t == null) return false;

        String userEmail = email.trim();
        User user = userRepository.findByEmail(userEmail)
                .orElseGet(() -> userRepository.findByEmail(userEmail.toLowerCase())
                .orElseGet(() -> userRepository.findById(userEmail)
                .orElseGet(() -> userRepository.findByCfHandleIgnoreCase(userEmail)
                .orElseGet(() -> userRepository.findByCodeforcesHandleIgnoreCase(userEmail)
                .orElse(null)))));

        if (user != null && user.isAdmin()) return true;

        String cf = user != null ? user.getCfHandle() : userEmail;

        if (isHost(t, cf, userEmail)) return true;

        if (t.getRegisteredPlayers() != null) {
            for (String p : t.getRegisteredPlayers()) {
                if (p != null && (p.equalsIgnoreCase(cf) || p.equalsIgnoreCase(userEmail))) {
                    return true;
                }
            }
        }

        if (t.getJoinRequests() != null) {
            for (String p : t.getJoinRequests()) {
                if (p != null && (p.equalsIgnoreCase(cf) || p.equalsIgnoreCase(userEmail))) {
                    return true;
                }
            }
        }

        return false;
    }

    private void populateCreatorEmailIfMissing(Tournament t) {
        if (t != null && (t.getCreatorEmail() == null || t.getCreatorEmail().isBlank()) && t.getCreatorId() != null) {
            try {
                userRepository.findByCodeforcesHandleIgnoreCase(t.getCreatorId())
                        .or(() -> userRepository.findByCfHandleIgnoreCase(t.getCreatorId()))
                        .ifPresent(u -> {
                            t.setCreatorEmail(u.getEmail());
                            tournamentRepository.save(t);
                        });
            } catch (Exception e) {
                log.debug("Could not backfill creator email: {}", e.getMessage());
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // CREATE TOURNAMENT
    // ═══════════════════════════════════════════════════════════
    public Tournament createTournament(String creatorCfHandle, String creatorEmail, String name, int maxPlayers,
            String difficulty, int matchDurationMinutes) {

        // Validate maxPlayers is power of 2
        if (maxPlayers != 8 && maxPlayers != 16 && maxPlayers != 32) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "maxPlayers must be 8, 16, or 32");
        }

        int totalRounds = (int) (Math.log(maxPlayers) / Math.log(2));

        Tournament t = new Tournament();
        t.setName(name);
        t.setCreatorId(creatorCfHandle);
        t.setCreatorEmail(creatorEmail);
        t.setDifficulty(difficulty);
        t.setMaxPlayers(maxPlayers);
        t.setMatchDurationMinutes(matchDurationMinutes);
        t.setStatus("REGISTRATION");
        t.setInviteCode(generateCode());
        t.setCreatedAt(new Date());
        t.setTotalRounds(totalRounds);
        t.setCurrentRound(0);

        // Host is referee and does NOT participate as a player
        t.setRegisteredPlayers(new ArrayList<>());
        t.setJoinRequests(new ArrayList<>());

        return tournamentRepository.save(t);
    }

    public Tournament createTournament(String creatorCfHandle, String name, int maxPlayers,
            String difficulty, int matchDurationMinutes) {
        return createTournament(creatorCfHandle, null, name, maxPlayers, difficulty, matchDurationMinutes);
    }

    // ═══════════════════════════════════════════════════════════
    // JOIN REQUEST (Players must send join request; host cannot play)
    // ═══════════════════════════════════════════════════════════
    public Tournament requestToJoin(String tournamentId, String cfHandle) {
        if (cfHandle == null || cfHandle.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Codeforces handle");
        }

        Tournament t = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tournament not found"));

        if (!"REGISTRATION".equals(t.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Registration is closed");
        }

        if (cfHandle.equalsIgnoreCase(t.getCreatorId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The tournament creator is the referee and cannot join as a player.");
        }

        User user = userRepository.findByCodeforcesHandleIgnoreCase(cfHandle)
                .orElseGet(() -> userRepository.findByCfHandleIgnoreCase(cfHandle).orElse(null));
        if (user != null && !user.isCfVerified()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Please verify your Codeforces account before requesting to join.");
        }

        if (t.getRegisteredPlayers().contains(cfHandle)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Already an accepted player in this tournament.");
        }

        if (t.getJoinRequests() == null) {
            t.setJoinRequests(new ArrayList<>());
        }

        if (t.getJoinRequests().contains(cfHandle)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Join request already sent.");
        }

        if (t.getRegisteredPlayers().size() >= t.getMaxPlayers()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tournament is full.");
        }

        t.getJoinRequests().add(cfHandle);
        Tournament saved = tournamentRepository.save(t);
        publishTournamentUpdate(saved);
        return saved;
    }

    // ═══════════════════════════════════════════════════════════
    // CANCEL JOIN REQUEST (Player cancels their own pending request)
    // ═══════════════════════════════════════════════════════════
    public Tournament cancelJoinRequest(String tournamentId, String cfHandle) {
        Tournament t = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tournament not found"));

        if (t.getJoinRequests() != null) {
            t.getJoinRequests().remove(cfHandle);
        }
        Tournament saved = tournamentRepository.save(t);
        publishTournamentUpdate(saved);
        return saved;
    }

    // ═══════════════════════════════════════════════════════════
    // ACCEPT JOIN REQUEST (Host only)
    // ═══════════════════════════════════════════════════════════
    public Tournament acceptJoinRequest(String tournamentId, String hostCfHandle, String hostEmail, String playerCfHandle) {
        Tournament t = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tournament not found"));

        populateCreatorEmailIfMissing(t);

        if (!isHost(t, hostCfHandle, hostEmail)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the host referee can accept join requests.");
        }

        if (!"REGISTRATION".equals(t.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tournament registration is closed.");
        }

        if (t.getJoinRequests() == null || !t.getJoinRequests().contains(playerCfHandle)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Join request not found for player: " + playerCfHandle);
        }

        if (t.getRegisteredPlayers().contains(playerCfHandle)) {
            t.getJoinRequests().remove(playerCfHandle);
            return tournamentRepository.save(t);
        }

        if (t.getRegisteredPlayers().size() >= t.getMaxPlayers()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tournament is already full.");
        }

        t.getJoinRequests().remove(playerCfHandle);
        t.getRegisteredPlayers().add(playerCfHandle);
        Tournament saved = tournamentRepository.save(t);
        publishTournamentUpdate(saved);
        return saved;
    }

    public Tournament acceptJoinRequest(String tournamentId, String hostCfHandle, String playerCfHandle) {
        return acceptJoinRequest(tournamentId, hostCfHandle, null, playerCfHandle);
    }

    // ═══════════════════════════════════════════════════════════
    // DECLINE JOIN REQUEST (Host only)
    // ═══════════════════════════════════════════════════════════
    public Tournament declineJoinRequest(String tournamentId, String hostCfHandle, String hostEmail, String playerCfHandle) {
        Tournament t = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tournament not found"));

        populateCreatorEmailIfMissing(t);

        if (!isHost(t, hostCfHandle, hostEmail)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the host referee can decline join requests.");
        }

        if (t.getJoinRequests() != null) {
            t.getJoinRequests().remove(playerCfHandle);
        }
        Tournament saved = tournamentRepository.save(t);
        publishTournamentUpdate(saved);
        return saved;
    }

    public Tournament declineJoinRequest(String tournamentId, String hostCfHandle, String playerCfHandle) {
        return declineJoinRequest(tournamentId, hostCfHandle, null, playerCfHandle);
    }

    // ═══════════════════════════════════════════════════════════
    // REGISTER PLAYER (Backward compatible - delegates to requestToJoin or auto-accepts if disabled)
    // ═══════════════════════════════════════════════════════════
    public Tournament registerPlayer(String tournamentId, String cfHandle) {
        return requestToJoin(tournamentId, cfHandle);
    }

    // ═══════════════════════════════════════════════════════════
    // UNREGISTER / LEAVE PLAYER
    // ═══════════════════════════════════════════════════════════
    public Tournament unregisterPlayer(String tournamentId, String cfHandle) {
        Tournament t = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tournament not found"));

        if (!"REGISTRATION".equals(t.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot unregister after tournament starts");
        }

        if (cfHandle.equalsIgnoreCase(t.getCreatorId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Creator is the referee and cannot register/unregister as a player");
        }

        if (t.getJoinRequests() != null) {
            t.getJoinRequests().remove(cfHandle);
        }
        t.getRegisteredPlayers().remove(cfHandle);
        Tournament saved = tournamentRepository.save(t);
        publishTournamentUpdate(saved);
        return saved;
    }

    // ═══════════════════════════════════════════════════════════
    // START ROUND MATCHES MANUALLY (Creator only)
    // ═══════════════════════════════════════════════════════════
    public Tournament startRoundMatchesManual(String tournamentId, String cfHandle, String email, int round, MatchService matchService) {
        Tournament t = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tournament not found"));

        populateCreatorEmailIfMissing(t);

        if (!isHost(t, cfHandle, email)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the creator can start rounds");
        }

        if (!"ONGOING".equals(t.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tournament is not ongoing");
        }

        if (t.getCurrentRound() != round) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Can only start the current active round");
        }

        // ✅ All matches in the same round share the same start and end time
        Date now = new Date();
        t.setRoundStartTime(now);
        Date roundEnd = new Date(now.getTime() + t.getMatchDurationMinutes() * 60 * 1000L);
        t.setRoundEndTime(roundEnd);
        t = tournamentRepository.save(t);

        // Use the existing logic to start the matches
        startRoundMatches(t, matchService);

        return t;
    }

    public Tournament startRoundMatchesManual(String tournamentId, String cfHandle, int round, MatchService matchService) {
        return startRoundMatchesManual(tournamentId, cfHandle, null, round, matchService);
    }

    // ═══════════════════════════════════════════════════════════
    // START TOURNAMENT (Creator only)
    // ═══════════════════════════════════════════════════════════
    public Tournament startTournament(String tournamentId, String cfHandle, String email) {
        Tournament t = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tournament not found"));

        populateCreatorEmailIfMissing(t);

        if (!isHost(t, cfHandle, email)) {
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

        // ✅ Set Round 1 timing
        Date now = new Date();
        t.setRoundStartTime(now);
        Date roundEnd = new Date(now.getTime() + t.getMatchDurationMinutes() * 60 * 1000L);
        t.setRoundEndTime(roundEnd);

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

        log.info("🏆 Tournament started: {} with {} players (Round 1 timing: {} -> {})",
                t.getName(), playerCount, now, roundEnd);
        
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

    public Tournament startTournament(String tournamentId, String cfHandle) {
        return startTournament(tournamentId, cfHandle, null);
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
            lobby.setPlayer1CFHandle(player1);
            lobby.setPlayer2CFHandle(player2);

            // Populate player IDs if user records exist
            userRepository.findByCodeforcesHandleIgnoreCase(player1)
                    .or(() -> userRepository.findByCfHandleIgnoreCase(player1))
                    .ifPresent(u -> lobby.setPlayer1Id(u.getId()));
            userRepository.findByCodeforcesHandleIgnoreCase(player2)
                    .or(() -> userRepository.findByCfHandleIgnoreCase(player2))
                    .ifPresent(u -> lobby.setPlayer2Id(u.getId()));

            lobby.setDurationMinutes(t.getMatchDurationMinutes());
            lobby.setStatus("READY"); // Both players pre-set
            lobby.setDifficulty(t.getDifficulty());
            lobby.setInviteCode(generateCode());
            lobby.setScore1(0);
            lobby.setScore2(0);
            lobby.setCurIdx(0);

            // Duration and shared round timing
            Date roundStart = t.getRoundStartTime();
            Date roundEnd = t.getRoundEndTime() != null ? t.getRoundEndTime() : (roundStart != null ? new Date(roundStart.getTime() + t.getMatchDurationMinutes() * 60 * 1000L) : new Date(t.getMatchDurationMinutes() * 60 * 1000L));
            lobby.setStartTime(roundStart);
            lobby.setEndTime(roundEnd);

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
    // Promotes MatchSecondary → MatchPrimary using synchronized tournament match start flow.
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

                // Start tournament match with synchronized timing and problem selection
                matchService.startTournamentMatch(t, code);
                log.info("  🚀 Round {} match started: {}", round, code);
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

    public Tournament advanceRoundManual(String tournamentId, String cfHandle, String email) {
        Tournament t = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tournament not found"));

        populateCreatorEmailIfMissing(t);

        if (!isHost(t, cfHandle, email)) {
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

        try {
            advanceRound(t);
        } catch (org.springframework.dao.OptimisticLockingFailureException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The round was already advanced.");
        }
        publishTournamentUpdate(t);
        return t;
    }

    public Tournament advanceRoundManual(String tournamentId, String cfHandle) {
        return advanceRoundManual(tournamentId, cfHandle, null);
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
                if (code == null || "BYE".equals(code)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Bracket data is inconsistent for " + player1 + " vs " + player2 + "; cannot advance.");
                }
                // Only a FINISHED result in secondary_matches may decide who advances — never a guess.
                MatchSecondary match = matchRepository2.findByInviteCode(code).orElse(null);
                if (match == null || !"FINISHED".equals(match.getStatus()) || match.getWinnerId() == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Match " + code + " has no final result yet.");
                }
                winner = match.getWinnerId();
                if ("DRAW".equals(winner)) {
                    winner = resolveTieBreakWinner(player1, player2);
                    match.setWinnerId(winner);
                    match.setEndedReason("DRAW_TIE_BREAK");
                    try {
                        matchRepository2.save(match);
                    } catch (Exception e) {
                        log.warn("Failed to persist tie-break winner to match {}: {}", code, e.getMessage());
                    }
                    log.info("Match {} ended in DRAW: tie-break advancing {}", code, winner);
                } else if (winner.equalsIgnoreCase(player1)) {
                    winner = player1;
                } else if (winner.equalsIgnoreCase(player2)) {
                    winner = player2;
                } else {
                    // e.g. a cancelled match ("CANCELLED") must never put a non-player into the next round
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Match " + code + " has no valid winner (" + winner + "); resolve it before advancing.");
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
            // Set shared timing for next round
            Date now = new Date();
            t.setRoundStartTime(now);
            Date roundEnd = new Date(now.getTime() + t.getMatchDurationMinutes() * 60 * 1000L);
            t.setRoundEndTime(roundEnd);

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

    /**
     * Resolves a tie-breaker when a tournament match ends in a DRAW.
     * Rule:
     * - The player with the higher Codeforces rating advances to the next round.
     * - If both players have the exact same rating (or both are unrated/unavailable),
     *   a random coin flip decides which player advances.
     */
    public String resolveTieBreakWinner(String player1, String player2) {
        int r1 = getPlayerRating(player1);
        int r2 = getPlayerRating(player2);

        log.info("⚔️ Tournament DRAW tie-breaker: {} (rating: {}) vs {} (rating: {})", player1, r1, player2, r2);

        if (r1 > r2) {
            log.info("🏆 {} advances with higher rating ({} > {})", player1, r1, r2);
            return player1;
        } else if (r2 > r1) {
            log.info("🏆 {} advances with higher rating ({} > {})", player2, r2, r1);
            return player2;
        } else {
            boolean coinFlip = random.nextBoolean();
            String chosen = coinFlip ? player1 : player2;
            log.info("🪙 Equal rating ({} == {}): coin flip randomly selected {} to advance", r1, r2, chosen);
            return chosen;
        }
    }

    public int getPlayerRating(String player) {
        if (player == null || player.isBlank()) return 0;
        String trimmed = player.trim();

        // 1. Try CodeforcesService
        try {
            if (codeforcesService != null) {
                Integer rating = codeforcesService.getUserRating(trimmed);
                if (rating != null) return rating;
            }
        } catch (Exception e) {
            log.warn("Failed to fetch rating from CodeforcesService for {}: {}", trimmed, e.getMessage());
        }

        // 2. Fallback: check User entity in DB if present
        try {
            User user = userRepository.findByCfHandleIgnoreCase(trimmed)
                    .orElseGet(() -> userRepository.findByCodeforcesHandleIgnoreCase(trimmed).orElse(null));
            if (user != null && user.getRating() != null) {
                return user.getRating();
            }
        } catch (Exception e) {
            log.debug("User lookup failed for rating of {}: {}", trimmed, e.getMessage());
        }

        return 0; // Default if unrated or unknown
    }

    // ═══════════════════════════════════════════════════════════
    // QUERY METHODS
    // ═══════════════════════════════════════════════════════════

    public Tournament getTournamentById(String id) {
        Tournament t = tournamentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tournament not found"));
        populateCreatorEmailIfMissing(t);
        return t;
    }

    public List<Tournament> getActiveTournaments() {
        List<Tournament> list = tournamentRepository.findByStatusIn(List.of("REGISTRATION", "ONGOING"));
        list.forEach(this::populateCreatorEmailIfMissing);
        return list;
    }

    public MatchSecondary getSecondaryMatch(String inviteCode) {
        return matchRepository2.findByInviteCode(inviteCode).orElse(null);
    }

    public List<Tournament> getMyTournaments(String cfHandle, String email) {
        List<Tournament> list = new ArrayList<>();
        if (cfHandle != null && !cfHandle.isBlank()) {
            list.addAll(tournamentRepository.findByRegisteredPlayersContainingOrCreatorId(cfHandle, cfHandle));
        }
        if (email != null && !email.isBlank()) {
            List<Tournament> byEmail = tournamentRepository.findByRegisteredPlayersContainingOrCreatorIdOrCreatorEmail(
                    cfHandle != null ? cfHandle : "",
                    cfHandle != null ? cfHandle : "",
                    email);
            for (Tournament t : byEmail) {
                if (list.stream().noneMatch(existing -> existing.getId().equals(t.getId()))) {
                    list.add(t);
                }
            }
        }
        list.forEach(this::populateCreatorEmailIfMissing);
        return list;
    }

    public List<Tournament> getMyTournaments(String cfHandle) {
        return getMyTournaments(cfHandle, null);
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
