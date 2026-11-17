package CF_DuelProject.CF_DuelProject.controller;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import CF_DuelProject.CF_DuelProject.dto.CfHandleRequest;
import CF_DuelProject.CF_DuelProject.model.User;
import CF_DuelProject.CF_DuelProject.repository.UserRepository;
import CF_DuelProject.CF_DuelProject.service.CFVerificationService;

import CF_DuelProject.CF_DuelProject.service.CodeforcesService;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping({"/api/user", "/user"})
@Slf4j
public class UserController {


    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CodeforcesService codeforcesService;

    @Autowired
    private CFVerificationService cfVerificationService;

    @GetMapping("/me")
    public ResponseEntity<?> getMe(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        String email = (String) authentication.getPrincipal();
        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        }

        // ✅ Never expose password or internal secrets in API response
        Map<String, Object> response = new HashMap<>();
        response.put("email", user.getEmail());
        response.put("name", user.getName());
        response.put("cfHandle", user.getCfHandle());
        response.put("cfVerified", user.isCfVerified());
        response.put("codeforcesHandle", user.getCodeforcesHandle());
        response.put("codeforcesVerified", user.isCodeforcesVerified());

        response.put("codeforcesVerifiedAt", user.getCodeforcesVerifiedAt() != null ? user.getCodeforcesVerifiedAt().toString() : null);
        response.put("admin", user.isAdmin());

        return ResponseEntity.ok(response);
    }

    @PostMapping({"/add-cf", "/verify-cf", "/generate-challenge"})
    public ResponseEntity<?> addCf(Authentication authentication,
                                   @RequestBody(required = false) CfHandleRequest req) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized", "message", "Unauthorized"));
        }

        String email = (String) authentication.getPrincipal();
        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            return ResponseEntity.status(404).body(Map.of("error", "User not found", "message", "User not found"));
        }

        String handle = req != null ? req.getCfHandle() : null;
        try {
            Map<String, Object> challenge = cfVerificationService.generateChallenge(user, handle);
            return ResponseEntity.ok(challenge);
        } catch (ResponseStatusException rse) {
            String msg = rse.getReason() != null ? rse.getReason() : rse.getMessage();
            return ResponseEntity.status(rse.getStatusCode()).body(Map.of(
                    "error", msg,
                    "message", msg,
                    "verified", false
            ));
        } catch (Exception ex) {
            log.error("Error in addCf for user {}: ", email, ex);
            String msg = (ex.getMessage() != null && !ex.getMessage().isBlank()) ? ex.getMessage() : "Failed to generate verification challenge. Please try again.";
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", msg,
                    "message", msg,
                    "verified", false
            ));
        }

    }

    @PostMapping({"/verify-challenge", "/check-verification"})
    public ResponseEntity<?> verifyChallenge(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized", "message", "Unauthorized"));
        }

        String email = (String) authentication.getPrincipal();
        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            return ResponseEntity.status(404).body(Map.of("error", "User not found", "message", "User not found"));
        }

        try {
            Map<String, Object> result = cfVerificationService.checkVerification(user);
            return ResponseEntity.ok(result);
        } catch (ResponseStatusException rse) {
            String msg = rse.getReason() != null ? rse.getReason() : rse.getMessage();
            return ResponseEntity.status(rse.getStatusCode()).body(Map.of(
                    "error", msg,
                    "message", msg,
                    "verified", false
            ));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "Verification check failed. Please try again.",
                    "message", "Verification check failed. Please try again.",
                    "verified", false
            ));
        }
    }

    @GetMapping({"/verification-challenge", "/active-challenge"})
    public ResponseEntity<?> getActiveChallenge(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized", "message", "Unauthorized"));
        }

        String email = (String) authentication.getPrincipal();
        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            return ResponseEntity.status(404).body(Map.of("error", "User not found", "message", "User not found"));
        }

        try {
            Map<String, Object> challenge = cfVerificationService.getActiveChallenge(user);
            return ResponseEntity.ok(challenge);
        } catch (ResponseStatusException rse) {
            String msg = rse.getReason() != null ? rse.getReason() : rse.getMessage();
            return ResponseEntity.status(rse.getStatusCode()).body(Map.of(
                    "error", msg,
                    "message", msg
            ));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "Failed to retrieve verification status.",
                    "message", "Failed to retrieve verification status."
            ));
        }
    }
}
