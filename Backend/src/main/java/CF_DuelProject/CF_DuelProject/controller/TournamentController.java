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
@RequestMapping("api/tournament")
public class TournamentController {

    private final TournamentService tournamentService;
    private final UserService userService;
    private final MatchService matchService;
    private final TournamentRepository tournamentRepository;
    // ✅ Injected for admin role check
    private final UserRepository userRepository;

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

        // ✅ Proper role-based authorization — check isAdmin flag in database
        User caller = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        if (!caller.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admins can create tournaments.");
        }

        String cfHandle = userService.getCfHandleByEmail(email);
        return tournamentService.createTournament(
                cfHandle, req.name, req.maxPlayers,
                req.difficulty, req.matchDurationMinutes);
    }

    // ✅ Register for Tournament
    @PostMapping("/{id}/register")
    public Tournament register(Authentication authentication,
            @PathVariable String id) {

        String email = (String) authentication.getPrincipal();
        String cfHandle = userService.getCfHandleByEmail(email);

        return tournamentService.registerPlayer(id, cfHandle);
    }

    // ✅ Unregister from Tournament
    @PostMapping("/{id}/unregister")
    public Tournament unregister(Authentication authentication,
            @PathVariable String id) {

        String email = (String) authentication.getPrincipal();
        String cfHandle = userService.getCfHandleByEmail(email);

        return tournamentService.unregisterPlayer(id, cfHandle);
    }

    @PostMapping("/{id}/start")
    public Tournament start(Authentication authentication,
            @PathVariable String id) {

        String email = (String) authentication.getPrincipal();
        String cfHandle = userService.getCfHandleByEmail(email);

        return tournamentService.startTournament(id, cfHandle);
    }

    // ✅ Start Round Matches Manually (Creator only)
    @PostMapping("/{id}/start-round/{round}")
    public Tournament startRoundManual(Authentication authentication,
            @PathVariable String id, @PathVariable int round) {

        String email = (String) authentication.getPrincipal();
        String cfHandle = userService.getCfHandleByEmail(email);

        return tournamentService.startRoundMatchesManual(id, cfHandle, round, matchService);
    }

    // ✅ Advance Round Manually (Creator only)
    @PostMapping("/{id}/advance-round")
    public Tournament advanceRoundManual(Authentication authentication,
            @PathVariable String id) {

        String email = (String) authentication.getPrincipal();
        String cfHandle = userService.getCfHandleByEmail(email);

        return tournamentService.advanceRoundManual(id, cfHandle);
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

        return tournamentService.getMyTournaments(cfHandle);
    }
}
