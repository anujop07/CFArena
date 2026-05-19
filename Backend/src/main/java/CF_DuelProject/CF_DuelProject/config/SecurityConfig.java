package CF_DuelProject.CF_DuelProject.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {
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
        config.setAllowedOriginPatterns(List.of("*")); // allow all origins
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

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

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/**", "/oauth2/**", "/login/**", "/ws/**", "/api/match/**").permitAll()
                        .anyRequest().authenticated())

                .oauth2Login(oauth -> oauth
                        .successHandler(handler))
                .oauth2Login(oauth -> oauth
                        .successHandler(handler)
                        .failureHandler((req, res, ex) -> {
                            System.out.println("OAuth2 failure: " + ex.getMessage());
                            String frontendUrl = System.getenv("FRONTEND_URL");
                            if (frontendUrl == null || frontendUrl.isEmpty())
                                frontendUrl = "http://localhost:5173";
                            res.sendRedirect(frontendUrl + "/login");
                        }))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, exx) -> {
                            res.setStatus(401);
                            res.getWriter().write("Unauthorized");
                        }))

                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public org.springframework.security.crypto.password.PasswordEncoder passwordEncoder() {
        return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
    }
}
