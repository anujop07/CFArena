package CF_DuelProject.CF_DuelProject.ratelimit;

import java.time.Duration;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import io.github.bucket4j.Bucket;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {
    private final RateLimiterService rateLimiterService;
    public RateLimitInterceptor(RateLimiterService rateLimiterService) {
        this.rateLimiterService = rateLimiterService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,HttpServletResponse response, Object handler) throws Exception {

        String path = request.getRequestURI();
        String key;

        if (request.getUserPrincipal() != null) {
            key = request.getUserPrincipal().getName(); // email
        } else {
            key = request.getRemoteAddr(); // IP
        }

        Bucket bucket = null;

        if (path.contains("/auth/login")) {
            bucket = rateLimiterService.resolveBucket(key + "_login", 5, 5, Duration.ofMinutes(1));
        }
        else if (path.contains("/auth/register")) {
            bucket = rateLimiterService.resolveBucket(key + "_register", 3, 3, Duration.ofMinutes(1));
        }
        else if (path.contains("/match/join")) {
            bucket = rateLimiterService.resolveBucket(key + "_join", 15, 15, Duration.ofMinutes(1));
        }
        else if (path.contains("/match/create")) {
            bucket = rateLimiterService.resolveBucket(key + "_create", 8, 8, Duration.ofMinutes(1));
        }
        else if (path.contains("/match/start")) {
            bucket = rateLimiterService.resolveBucket(key + "_start", 15, 15, Duration.ofMinutes(1));
        }
        else if (path.contains("/user/add-cf")) {
            bucket = rateLimiterService.resolveBucket(key + "_addcf", 5, 5, Duration.ofMinutes(1));
        }
        if (bucket != null && !bucket.tryConsume(1)) {
            response.setStatus(429);
            response.getWriter().write("Too many requests");
            return false;
        }

        return true;
    }
}