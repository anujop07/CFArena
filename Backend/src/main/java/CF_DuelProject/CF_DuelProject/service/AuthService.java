package CF_DuelProject.CF_DuelProject.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import CF_DuelProject.CF_DuelProject.dto.*;
import CF_DuelProject.CF_DuelProject.model.User;
import CF_DuelProject.CF_DuelProject.repository.UserRepository;


@Service
public class AuthService {
    
    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JwtService jwtService;

    public void register(RegisterRequest req) {

        if(userRepository.findByEmail(req.getEmail()).isPresent()) {
            throw new RuntimeException("User exists");
        }

        User user = new User();
        user.setName(req.getName());
        user.setEmail(req.getEmail());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setProvider("LOCAL");
        user.setCfHandle(req.getCfHandle() != null ? req.getCfHandle().trim() : null);
        user.setCodeforcesVerified(false);

        user.setCodeforcesVerifiedAt(null);

        try {
            userRepository.save(user);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new RuntimeException("User exists");
        }
    }

    public String login(AuthRequest req) {

        User user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if(!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new RuntimeException("Wrong password");
        }

        return jwtService.generateToken(user.getEmail());
    }
}
