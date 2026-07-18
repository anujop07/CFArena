package CF_DuelProject.CF_DuelProject.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import CF_DuelProject.CF_DuelProject.dto.CfHandleRequest;
import CF_DuelProject.CF_DuelProject.model.User;
import CF_DuelProject.CF_DuelProject.repository.UserRepository;

@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    UserRepository userRepository;

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

        // ✅ Never expose password or internal fields in API response
        Map<String, Object> response = new HashMap<>();
        response.put("email", user.getEmail());
        response.put("name", user.getName());
        response.put("cfHandle", user.getCfHandle());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/add-cf")
    public ResponseEntity<?> addCf(Authentication authentication,
                                   @RequestBody CfHandleRequest req) {

        // ✅ Use Spring Security's Authentication object — NOT manual header parsing
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        String email = (String) authentication.getPrincipal();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        // Basic input validation
        String cfHandle = req.getCfHandle();
        if (cfHandle == null || cfHandle.isBlank() || cfHandle.length() > 50) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid CF handle"));
        }

        user.setCfHandle(cfHandle.trim());
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("message", "CF handle updated"));
    }
}
