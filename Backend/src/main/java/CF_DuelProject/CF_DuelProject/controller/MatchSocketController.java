package CF_DuelProject.CF_DuelProject.controller;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import CF_DuelProject.CF_DuelProject.dto.ChatMessage;
import CF_DuelProject.CF_DuelProject.dto.EmoteMessage;
import CF_DuelProject.CF_DuelProject.dto.SkipVoteMessage;
import CF_DuelProject.CF_DuelProject.service.MatchService;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class MatchSocketController {

    private final SimpMessagingTemplate messagingTemplate;
    private final MatchService matchService;

    @MessageMapping("/match/chat")
    public void handleChat(@Payload ChatMessage message) {
        messagingTemplate.convertAndSend(
            "/topic/match/" + message.getInviteCode() + "/chat",
            message
        );
    }

    @MessageMapping("/match/emote")
    public void handleEmote(@Payload EmoteMessage message) {
        messagingTemplate.convertAndSend(
            "/topic/match/" + message.getInviteCode() + "/emotes",
            message
        );
    }

    @MessageMapping("/match/skip")
    public void handleSkipVote(@Payload SkipVoteMessage message) {
        matchService.handleSkipVote(message.getInviteCode(), message.getSender());
    }
}
