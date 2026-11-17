package CF_DuelProject.CF_DuelProject.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import CF_DuelProject.CF_DuelProject.model.User;
import CF_DuelProject.CF_DuelProject.repository.UserRepository;

@Service
public class UserService {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + email));
    }

    public String getCfHandleByEmail(String email) {
        User user = getUserByEmail(email);

        if (user.getCfHandle() == null || user.getCfHandle().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Codeforces handle not connected for user: " + email);
        }

        return user.getCfHandle();
    }

    public String getVerifiedCfHandleByEmail(String email) {
        User user = getUserByEmail(email);

        if (!user.isCfVerified() || user.getCfHandle() == null || user.getCfHandle().isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User not verified: please verify your Codeforces account before registering for a tournament.");
        }

        return user.getCfHandle();
    }

    public boolean isCodeforcesVerified(String email) {
        User user = userRepository.findByEmail(email).orElse(null);
        return user != null && user.isCodeforcesVerified();
    }
}

