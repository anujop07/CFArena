package CF_DuelProject.CF_DuelProject.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import CF_DuelProject.CF_DuelProject.dto.CreateTournamentRequest;
import CF_DuelProject.CF_DuelProject.model.Tournament;
import CF_DuelProject.CF_DuelProject.model.User;
import CF_DuelProject.CF_DuelProject.repository.TournamentRepository;
import CF_DuelProject.CF_DuelProject.repository.UserRepository;
import CF_DuelProject.CF_DuelProject.service.MatchService;
import CF_DuelProject.CF_DuelProject.service.TournamentService;
import CF_DuelProject.CF_DuelProject.service.UserService;

@RestController
@RequestMapping({"/api/tournament", "/tournament"})
public class TournamentController {

    private final TournamentService tournamentService;
    private final UserService userService;
    private final MatchService matchService;
    private final TournamentRepository tournamentRepository;
    // ✅ Injected for admin role check
    private final UserRepository userRepository;

    @org.springframework.beans.factory.annotation.Value("${app.tournament.admin-only:true}")
    private boolean adminOnly = true;

    @org.springframework.beans.factory.annotation.Value("${app.admin-emails:}")
    private String adminEmails = "";

    public TournamentController(TournamentService tournamentService,
                                UserService userService,
                                MatchService matchService,
                                TournamentRepository tournamentRepository,
                                UserRepository userRepository) {
        this.tournamentService = tournamentService;
        this.userService = userService;
        this.matchService = matchService;
        this.tournamentRepository = tournamentRepository;
        this.userRepository = userRepository;
    }

    // ✅ Create Tournament — Admin only (isAdmin flag in DB)
    @PostMapping("/create")
    public Tournament create(Authentication authentication,
            @RequestBody CreateTournamentRequest req) {

        String email = (String) authentication.getPrincipal();

        // ✅ Verify user exists
        User caller = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        if (adminOnly && !isOrganizer(caller)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only organizers can create tournaments.");
        }

        String cfHandle = userService.getVerifiedCfHandleByEmail(email);
        return tournamentService.createTournament(
                cfHandle, email, req.name, req.maxPlayers,
                req.difficulty, req.matchDurationMinutes);
    }

    private boolean isOrganizer(User caller) {
        if (caller.isAdmin()) return true;
        if (caller.getEmail() == null || adminEmails == null) return false;
        for (String allowed : adminEmails.split(",")) {
            if (!allowed.isBlank() && allowed.trim().equalsIgnoreCase(caller.getEmail().trim())) return true;
        }
        return false;
    }

    // ✅ Request to Join Tournament (Player sends request)
    @PostMapping("/{id}/join-request")
    public Tournament requestToJoin(Authentication authentication,
            @PathVariable String id) {

        String email = (String) authentication.getPrincipal();
        String cfHandle = userService.getVerifiedCfHandleByEmail(email);

        return tournamentService.requestToJoin(id, cfHandle);
    }

    // ✅ Cancel Join Request (Player cancels their request)
    @PostMapping("/{id}/join-request/cancel")
    public Tournament cancelJoinRequest(Authentication authentication,
            @PathVariable String id) {

        String email = (String) authentication.getPrincipal();
        String cfHandle = userService.getVerifiedCfHandleByEmail(email);

        return tournamentService.cancelJoinRequest(id, cfHandle);
    }

    // ✅ Accept Player Join Request (Host only)
    @PostMapping("/{id}/accept-request")
    public Tournament acceptRequest(Authentication authentication,
            @PathVariable String id,
            @RequestParam String playerHandle) {

        String email = (String) authentication.getPrincipal();
        String cfHandle = userService.getVerifiedCfHandleByEmail(email);

        return tournamentService.acceptJoinRequest(id, cfHandle, email, playerHandle);
    }

    // ✅ Decline Player Join Request (Host only)
    @PostMapping("/{id}/decline-request")
    public Tournament declineRequest(Authentication authentication,
            @PathVariable String id,
            @RequestParam String playerHandle) {

        String email = (String) authentication.getPrincipal();
        String cfHandle = userService.getVerifiedCfHandleByEmail(email);

        return tournamentService.declineJoinRequest(id, cfHandle, email, playerHandle);
    }

    // ✅ Register for Tournament (backward compatibility)
    @PostMapping("/{id}/register")
    public Tournament register(Authentication authentication,
            @PathVariable String id) {

        String email = (String) authentication.getPrincipal();
        String cfHandle = userService.getVerifiedCfHandleByEmail(email);

        return tournamentService.registerPlayer(id, cfHandle);
    }

    // ✅ Unregister from Tournament
    @PostMapping("/{id}/unregister")
    public Tournament unregister(Authentication authentication,
            @PathVariable String id) {

        String email = (String) authentication.getPrincipal();
        String cfHandle = userService.getVerifiedCfHandleByEmail(email);

        return tournamentService.unregisterPlayer(id, cfHandle);
    }

    @PostMapping("/{id}/start")
    public Tournament start(Authentication authentication,
            @PathVariable String id) {

        String email = (String) authentication.getPrincipal();
        String cfHandle = userService.getVerifiedCfHandleByEmail(email);

        return tournamentService.startTournament(id, cfHandle, email);
    }

    // ✅ Start Round Matches Manually (Creator only)
    @PostMapping("/{id}/start-round/{round}")
    public Tournament startRoundManual(Authentication authentication,
            @PathVariable String id, @PathVariable int round) {

        String email = (String) authentication.getPrincipal();
        String cfHandle = userService.getVerifiedCfHandleByEmail(email);

        return tournamentService.startRoundMatchesManual(id, cfHandle, email, round, matchService);
    }

    // ✅ Advance Round Manually (Creator only)
    @PostMapping("/{id}/advance-round")
    public Tournament advanceRoundManual(Authentication authentication,
            @PathVariable String id) {

        String email = (String) authentication.getPrincipal();
        String cfHandle = userService.getVerifiedCfHandleByEmail(email);

        return tournamentService.advanceRoundManual(id, cfHandle, email);
    }

    // ✅ Get Tournament Details
    @GetMapping("/{id}")
    public Tournament getById(@PathVariable String id) {
        return tournamentService.getTournamentById(id);
    }

    // ✅ Get Full Bracket (with match details per round)
    @GetMapping("/{id}/bracket")
    public Map<String, Object> getBracket(@PathVariable String id) {
        return tournamentService.getFullBracket(id);
    }

    // ✅ List Active Tournaments
    @GetMapping("/active")
    public List<Tournament> getActive() {
        return tournamentService.getActiveTournaments();
    }

    // ✅ My Tournaments
    @GetMapping("/my")
    public List<Tournament> getMy(Authentication authentication) {
        String email = (String) authentication.getPrincipal();
        String cfHandle = userService.getCfHandleByEmail(email);

        return tournamentService.getMyTournaments(cfHandle, email);
    }
}
