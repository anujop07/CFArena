package CF_DuelProject.CF_DuelProject.controller;

import java.security.Principal;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import CF_DuelProject.CF_DuelProject.dto.ChatMessage;
import CF_DuelProject.CF_DuelProject.dto.EmoteMessage;
import CF_DuelProject.CF_DuelProject.dto.SkipVoteMessage;
import CF_DuelProject.CF_DuelProject.service.MatchService;
import CF_DuelProject.CF_DuelProject.service.TournamentService;
import lombok.extern.slf4j.Slf4j;

/**
 * Realtime side-channel for a duel. The sender is always derived from the authenticated session and
 * checked against the match's players — the {@code sender} field sent by the client is ignored.
 */
@Controller
@Slf4j
public class MatchSocketController {

    static final int MAX_CHAT_LENGTH = 500;
    static final int MAX_EMOTE_LENGTH = 32;

    private final SimpMessagingTemplate messagingTemplate;
    private final MatchService matchService;
    private final TournamentService tournamentService;

    public MatchSocketController(SimpMessagingTemplate messagingTemplate, MatchService matchService) {
        this(messagingTemplate, matchService, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public MatchSocketController(SimpMessagingTemplate messagingTemplate, MatchService matchService, @org.springframework.context.annotation.Lazy TournamentService tournamentService) {
        this.messagingTemplate = messagingTemplate;
        this.matchService = matchService;
        this.tournamentService = tournamentService;
    }

    @MessageMapping("/match/chat")
    public void handleChat(@Payload ChatMessage message, Principal principal) {
        String handle = authorize(principal, message == null ? null : message.getInviteCode());
        if (handle == null || message.getText() == null || message.getText().isBlank()) return;

        String text = message.getText().strip();
        if (text.length() > MAX_CHAT_LENGTH) text = text.substring(0, MAX_CHAT_LENGTH);
        message.setText(text);
        message.setSender(handle);
        String code = MatchService.normalizeCode(message.getInviteCode());
        messagingTemplate.convertAndSend(
            "/topic/match/" + code + "/chat",
            message
        );
        if (tournamentService != null && tournamentService.isTournament(message.getInviteCode())) {
            messagingTemplate.convertAndSend("/topic/tournament/" + message.getInviteCode() + "/chat", message);
            messagingTemplate.convertAndSend("/topic/tournament/" + code + "/chat", message);
        }
    }

    @MessageMapping("/match/emote")
    public void handleEmote(@Payload EmoteMessage message, Principal principal) {
        String handle = authorize(principal, message == null ? null : message.getInviteCode());
        if (handle == null || message.getEmote() == null || message.getEmote().isBlank()) return;

        String emote = message.getEmote();
        if (emote.length() > MAX_EMOTE_LENGTH) emote = emote.substring(0, MAX_EMOTE_LENGTH);
        message.setEmote(emote);
        message.setSender(handle);
        String code = MatchService.normalizeCode(message.getInviteCode());
        messagingTemplate.convertAndSend(
            "/topic/match/" + code + "/emotes",
            message
        );
        if (tournamentService != null && tournamentService.isTournament(message.getInviteCode())) {
            messagingTemplate.convertAndSend("/topic/tournament/" + message.getInviteCode() + "/emotes", message);
            messagingTemplate.convertAndSend("/topic/tournament/" + code + "/emotes", message);
        }
    }

    @MessageMapping("/match/skip")
    public void handleSkipVote(@Payload SkipVoteMessage message, Principal principal) {
        String handle = authorize(principal, message == null ? null : message.getInviteCode());
        if (handle == null) return;
        matchService.handleSkipVote(message.getInviteCode(), handle);
    }

    /** Returns the caller's CF handle if authenticated and a player in the match, else null (message dropped). */
    private String authorize(Principal principal, String inviteCode) {
        if (principal == null || inviteCode == null) return null;
        String handle = matchService.resolveParticipantHandle(principal.getName(), inviteCode);
        if (handle == null) {
            log.warn("Dropped websocket message from {} for match {}: not a participant", principal.getName(), inviteCode);
        }
        return handle;
    }
}
