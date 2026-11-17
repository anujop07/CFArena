package CF_DuelProject.CF_DuelProject.controller;


import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import CF_DuelProject.CF_DuelProject.dto.ChatMessage;
import CF_DuelProject.CF_DuelProject.dto.CreateMatchRequest;
import CF_DuelProject.CF_DuelProject.dto.EmoteMessage;
import CF_DuelProject.CF_DuelProject.model.MatchPrimary;
import CF_DuelProject.CF_DuelProject.model.MatchSecondary;
import CF_DuelProject.CF_DuelProject.service.MatchService;
import CF_DuelProject.CF_DuelProject.service.UserService;

import java.util.Map;

@RestController
@RequestMapping({"/api/match", "/match"})
public class MatchController {

    static final int MIN_DURATION_MINUTES = 1;
    static final int MAX_DURATION_MINUTES = 300;

    private final MatchService matchService;
    private final UserService userService;

    public MatchController(MatchService matchService, UserService userService) {
        this.matchService = matchService;
        this.userService = userService;
    }

    // ✅ CREATE (Requires verified Codeforces account)
    @PostMapping("/create")
    public MatchSecondary create(Authentication authentication,
                        @RequestBody(required = false) CreateMatchRequest req) {

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        String email = authentication.getName();
        String cfHandle = userService.getVerifiedCfHandleByEmail(email);

        int duration = (req != null && req.getDuration() > 0) ? req.getDuration() : 30;
        if (duration < MIN_DURATION_MINUTES || duration > MAX_DURATION_MINUTES) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Duration must be between " + MIN_DURATION_MINUTES + " and " + MAX_DURATION_MINUTES + " minutes");
        }
        String difficulty = (req != null && req.getDifficulty() != null && !req.getDifficulty().isBlank()) ? req.getDifficulty() : "800";

        return matchService.createMatch(cfHandle, duration, difficulty);
    }


    // ✅ JOIN (Requires verified Codeforces account)
    @PostMapping("/join")
    public MatchSecondary join(Authentication authentication,
                      @RequestParam String inviteCode) {

        String email = authentication.getName();
        String cfHandle = userService.getVerifiedCfHandleByEmail(email);

        return matchService.joinMatch(cfHandle, inviteCode);
    }

    // 🟢 START (Requires both players verified)
    @PostMapping("/start")
    public MatchPrimary start(Authentication authentication,
                       @RequestParam String inviteCode) {

        String email = authentication.getName();
        String cfHandle = userService.getVerifiedCfHandleByEmail(email);

        return matchService.startMatch(cfHandle, inviteCode);
    }

    @GetMapping("/status")
    public Map<String, Object> getStatus(Authentication authentication, @RequestParam String inviteCode) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        // only the two players (or the tournament organizer / an admin) may read a match
        return matchService.getMatchStatusFor(inviteCode, authentication.getName());
    }

    // ✅ IDOR fix: users can only fetch their own match history
    @GetMapping("/history")
    public ResponseEntity<?> getMatchHistory(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }
        String email = authentication.getName();
        return ResponseEntity.ok(matchService.getUserMatchHistory(email));
    }

    @GetMapping("/history/{userId}")
    public ResponseEntity<?> getMatchHistoryForUser(Authentication authentication, @PathVariable String userId) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }
        // Only the owner may read an account's history by identifier (email / CF handle / id).
        if (!matchService.isSameAccount(authentication.getName(), userId)) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "You can only view your own match history");
        }
        return ResponseEntity.ok(matchService.getUserMatchHistory(userId));
    }

    // 🛑 STOP MATCH (Creator only)
    @PostMapping("/{identifier}/stop")
    public ResponseEntity<?> stopMatch(Authentication authentication, @PathVariable String identifier) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of("message", "Authentication required"));
        }
        String requester = authentication.getName();
        MatchSecondary stopped = matchService.stopMatchByCreator(identifier, requester);
        return ResponseEntity.ok(stopped);
    }

    @PostMapping("/stop")
    public ResponseEntity<?> stopMatchQuery(Authentication authentication,
                                            @RequestParam(required = false) String inviteCode,
                                            @RequestParam(required = false) String matchId) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of("message", "Authentication required"));
        }
        String identifier = (inviteCode != null && !inviteCode.isBlank()) ? inviteCode : matchId;
        if (identifier == null || identifier.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "inviteCode or matchId is required"));
        }
        String requester = authentication.getName();
        MatchSecondary stopped = matchService.stopMatchByCreator(identifier, requester);
        return ResponseEntity.ok(stopped);
    }

    @PostMapping("/skip")
    public ResponseEntity<?> skip(Authentication authentication, @RequestParam String inviteCode) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        String handle = matchService.resolveParticipantHandle(authentication.getName(), inviteCode);
        if (handle == null) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "You are not a participant of this match");
        }
        matchService.handleSkipVote(inviteCode, handle);
        return ResponseEntity.ok(Map.of("success", true, "voter", handle));
    }

    @PostMapping("/chat")
    public ResponseEntity<?> chat(Authentication authentication, @RequestBody ChatMessage req) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        if (req == null || req.getInviteCode() == null || req.getText() == null) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "inviteCode and text are required");
        }
        String handle = matchService.resolveParticipantHandle(authentication.getName(), req.getInviteCode());
        if (handle == null) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "You are not a participant of this match");
        }
        matchService.sendChatMessage(req.getInviteCode(), authentication.getName(), req.getText());
        return ResponseEntity.ok(Map.of("success", true, "sender", handle, "text", req.getText()));
    }

    @PostMapping("/emote")
    public ResponseEntity<?> emote(Authentication authentication, @RequestBody EmoteMessage req) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        if (req == null || req.getInviteCode() == null || req.getEmote() == null) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "inviteCode and emote are required");
        }
        String handle = matchService.resolveParticipantHandle(authentication.getName(), req.getInviteCode());
        if (handle == null) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "You are not a participant of this match");
        }
        matchService.sendEmoteMessage(req.getInviteCode(), authentication.getName(), req.getEmote());
        return ResponseEntity.ok(Map.of("success", true, "sender", handle, "emote", req.getEmote()));
    }

}