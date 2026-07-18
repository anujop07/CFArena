package CF_DuelProject.CF_DuelProject.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;

@Service
public class JwtService {

    // ✅ No fallback — app MUST fail to start if JWT_SECRET is missing
    @Value("${JWT_SECRET}")
    private String SECRET;

    private Key getKey() {
        return Keys.hmacShaKeyFor(SECRET.getBytes());
    }

    // ✅ Shared helper — no more duplicated parsing logic
    private Claims getClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public String generateToken(String email) {
        long NOW = System.currentTimeMillis();
        long EXPIRY = 1000L * 60 * 60 * 24; // 24 hours

        return Jwts.builder()
                .setSubject(email)
                .setIssuedAt(new Date(NOW))
                .setExpiration(new Date(NOW + EXPIRY)) // ✅ expiry was missing
                .signWith(getKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractEmail(String token) {
        return getClaims(token).getSubject(); // ✅ reuses getClaims
    }

    public boolean isTokenValid(String token) {
        try {
            return !getClaims(token).getExpiration().before(new Date()); // ✅ works now
        } catch (Exception e) {
            return false;
        }
    }
}