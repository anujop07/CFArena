package CF_DuelProject.CF_DuelProject.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@Slf4j
public class SecurityConfig {

    @Value("${frontend.url}")
    private String frontendUrl;

    private final CF_DuelProject.CF_DuelProject.service.JwtService jwtService;
    private final CF_DuelProject.CF_DuelProject.config.JwtFilter jwtFilter;

    SecurityConfig(CF_DuelProject.CF_DuelProject.config.JwtFilter jwtFilter,
            CF_DuelProject.CF_DuelProject.service.JwtService jwtService) {
        this.jwtFilter = jwtFilter;
        this.jwtService = jwtService;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // ✅ Only allow the actual frontend domain — no wildcards in production
        config.setAllowedOriginPatterns(List.of(frontendUrl));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
            OAuth2SuccessHandler handler) throws Exception {

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                .csrf(csrf -> csrf.disable())

                // Removed STATELESS session policy because Spring Security OAuth2 requires 
                // temporary sessions to store the 'state' parameter during the Google redirect flow.
                // We still use JWT for API authentication.

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/**", "/oauth2/**", "/login/**", "/ws/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET,
                                "/api/tournament/active",
                                "/api/tournament/*/bracket",
                                "/api/tournament/*").permitAll()
                        .anyRequest().authenticated())

                .oauth2Login(oauth -> oauth
                        .successHandler(handler)
                        .failureHandler((req, res, ex) -> {
                            log.warn("OAuth2 login failure: {}", ex.getMessage());
                            res.sendRedirect(frontendUrl + "/login");
                        }))

                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, exx) -> {
                            res.setStatus(401);
                            res.setContentType("application/json");
                            res.getWriter().write("{\"error\":\"Unauthorized\"}");
                        }))

                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public org.springframework.security.crypto.password.PasswordEncoder passwordEncoder() {
        return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
    }
}
