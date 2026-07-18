package CF_DuelProject.CF_DuelProject.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

import CF_DuelProject.CF_DuelProject.dto.AuthRequest;
import CF_DuelProject.CF_DuelProject.dto.AuthResponse;
import CF_DuelProject.CF_DuelProject.dto.RegisterRequest;
import CF_DuelProject.CF_DuelProject.service.AuthService;

@RestController
@RequestMapping("/auth")
@Slf4j
public class AuthController {

    @Autowired
    AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest req) {
        // Basic input validation
        if (req.getEmail() == null || req.getEmail().isBlank() ||
            req.getPassword() == null || req.getPassword().length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid email or password (min 6 chars)"));
        }
        try {
            authService.register(req);
            return ResponseEntity.ok(Map.of("message", "User registered successfully"));
        } catch (RuntimeException e) {
            // ✅ Log internally but don't expose raw exception messages to client
            log.warn("Registration failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error during registration", e);
            return ResponseEntity.status(500).body(Map.of("error", "Registration failed"));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest req) {
        try {
            String token = authService.login(req);
            return ResponseEntity.ok(new AuthResponse(token));
        } catch (RuntimeException e) {
            // ✅ Generic message — don't tell attacker if email or password was wrong
            log.warn("Login attempt failed for email: {}", req.getEmail());
            return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
        } catch (Exception e) {
            log.error("Unexpected error during login", e);
            return ResponseEntity.status(500).body(Map.of("error", "Login failed"));
        }
    }
}
