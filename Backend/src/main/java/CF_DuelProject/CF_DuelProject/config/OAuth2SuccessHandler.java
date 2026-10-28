package CF_DuelProject.CF_DuelProject.config;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import CF_DuelProject.CF_DuelProject.model.User;
import CF_DuelProject.CF_DuelProject.repository.UserRepository;
import CF_DuelProject.CF_DuelProject.service.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Slf4j
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    @Value("${frontend.url}")
    private String frontendUrl;

    @Autowired
    UserRepository userRepository;

    @Autowired
    JwtService jwtService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

        String email = oAuth2User.getAttribute("email");
        Boolean emailVerified = oAuth2User.getAttribute("email_verified");
        if (email == null || email.isBlank() || !Boolean.TRUE.equals(emailVerified)) {
            log.warn("OAuth2 login rejected: e-mail missing or not verified by Google");
            getRedirectStrategy().sendRedirect(request, response, frontendUrl + "/login");
            return;
        }
        String name  = oAuth2User.getAttribute("name");

        // Find existing user or create a new one via Google OAuth
        User user = userRepository.findByEmail(email)
                .orElseGet(() -> {
                    User u = new User();
                    u.setEmail(email);
                    u.setName(name);
                    u.setProvider("GOOGLE");
                    u.setCodeforcesVerified(false);
                    return userRepository.save(u);
                });

        log.info("OAuth2 login success for user: {}", email);

        String token = jwtService.generateToken(email);
        // URL fragment: browsers never send it to servers, proxies or in Referer headers, so it stays out of access logs.
        String redirectUrl = frontendUrl + "/auth/callback#token=" + token;

        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}