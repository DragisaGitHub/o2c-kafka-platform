package rs.master.o2c.auth.bff;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class BffAuthController {
    private final BffSessionService sessions;
    private final BffCookieProperties cookieProps;

    public BffAuthController(
            BffSessionService sessions,
            BffCookieProperties cookieProps
    ) {
        this.sessions = sessions;
        this.cookieProps = cookieProps;
    }

    public record SessionResponse(String username) {}

    @PostMapping("/logout")
    public Mono<ResponseEntity<Void>> logout(org.springframework.http.server.reactive.ServerHttpRequest request) {
        var cookie = request.getCookies().getFirst(BffSessionService.COOKIE_NAME);
        if (cookie != null) {
            sessions.invalidate(cookie.getValue());
        }

        var expired = cookieProps.expiredCookie(BffSessionService.COOKIE_NAME, request);

        return Mono.just(ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, expired.toString())
                .build());
    }

    @GetMapping("/api/session")
    public Mono<ResponseEntity<SessionResponse>> session(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return Mono.just(ResponseEntity.status(401).build());
        }

        return Mono.just(ResponseEntity.ok(new SessionResponse(authentication.getName())));
    }
}
