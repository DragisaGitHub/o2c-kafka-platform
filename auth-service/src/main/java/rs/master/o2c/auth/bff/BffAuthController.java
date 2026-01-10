package rs.master.o2c.auth.bff;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import rs.master.o2c.auth.config.AuthJwtProperties;
import rs.master.o2c.auth.service.InMemoryUsers;
import rs.master.o2c.auth.service.JwtService;

import java.time.Duration;

@RestController
public class BffAuthController {

    private final InMemoryUsers users;
    private final JwtService jwtService;
    private final AuthJwtProperties jwtProps;
    private final BffSessionService sessions;

    public BffAuthController(
            InMemoryUsers users,
            JwtService jwtService,
            AuthJwtProperties jwtProps,
            BffSessionService sessions
    ) {
        this.users = users;
        this.jwtService = jwtService;
        this.jwtProps = jwtProps;
        this.sessions = sessions;
    }

    public record LoginRequest(String username, String password) {}

    public record LoginResponse(String status, String username) {}

    public record SessionResponse(String username) {}

    @PostMapping("/login")
    public Mono<ResponseEntity<LoginResponse>> login(@RequestBody Mono<LoginRequest> body) {
        return body.map(req -> {
            if (req.username() == null || req.password() == null) {
                return ResponseEntity.badRequest().build();
            }

            boolean ok = users.validCredentials(req.username(), req.password());
            if (!ok) {
                return ResponseEntity.status(401).build();
            }

            String token = jwtService.generateAccessToken(req.username());
            Duration ttl = Duration.ofMinutes(jwtProps.expiresInMinutes());
            String sessionId = sessions.createSession(req.username(), token, ttl);

            ResponseCookie cookie = ResponseCookie.from(BffSessionService.COOKIE_NAME, sessionId)
                    .httpOnly(true)
                    .secure(false)
                    .path("/")
                    .sameSite("Lax")
                    .maxAge(ttl)
                    .build();

            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, cookie.toString())
                    .body(new LoginResponse("AUTHENTICATED", req.username()));
        });
    }

    @PostMapping("/logout")
    public Mono<ResponseEntity<Void>> logout(org.springframework.http.server.reactive.ServerHttpRequest request) {
        var cookie = request.getCookies().getFirst(BffSessionService.COOKIE_NAME);
        if (cookie != null) {
            sessions.invalidate(cookie.getValue());
        }

        ResponseCookie expired = ResponseCookie.from(BffSessionService.COOKIE_NAME, "")
                .httpOnly(true)
                .secure(false)
                .path("/")
                .sameSite("Lax")
                .maxAge(Duration.ZERO)
                .build();

        return Mono.just(ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, expired.toString())
                .build());
    }

    @GetMapping("/api/session")
    public Mono<ResponseEntity<SessionResponse>> session(org.springframework.http.server.reactive.ServerHttpRequest request) {
        var cookie = request.getCookies().getFirst(BffSessionService.COOKIE_NAME);
        if (cookie == null) {
            return Mono.just(ResponseEntity.status(401).build());
        }

        var session = sessions.getValidSession(cookie.getValue());
        if (session == null) {
            return Mono.just(ResponseEntity.status(401).build());
        }

        return Mono.just(ResponseEntity.ok(new SessionResponse(session.username())));
    }
}
