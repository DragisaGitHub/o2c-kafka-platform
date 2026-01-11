package rs.master.o2c.auth.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import rs.master.o2c.auth.bff.BffCookieProperties;
import rs.master.o2c.auth.bff.BffSessionService;
import rs.master.o2c.auth.api.dto.LoginRequest;
import rs.master.o2c.auth.api.dto.LoginResponse;
import rs.master.o2c.auth.api.dto.VerifyMfaRequest;
import rs.master.o2c.auth.api.dto.VerifyMfaResponse;
import rs.master.o2c.auth.config.AuthJwtProperties;
import rs.master.o2c.auth.service.JwtService;
import rs.master.o2c.auth.service.PinChallengeService;
import rs.master.o2c.auth.service.UserService;

@RequiredArgsConstructor
@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final UserService userService;
    private final PinChallengeService pinChallengeService;
    private final JwtService jwtService;
    private final AuthJwtProperties jwtProps;
    private final BffSessionService sessions;
    private final BffCookieProperties cookieProps;
    private final Environment environment;

    @PostMapping("/login")
    public Mono<ResponseEntity<LoginResponse>> login(@Valid @RequestBody Mono<LoginRequest> body) {
        return body.flatMap(req -> userService.validateCredentials(req.username(), req.password())
                .map(u -> {
                    var ch = pinChallengeService.createChallenge(u.username());

                    if (environment.acceptsProfiles(Profiles.of("local", "dev"))) {
                        log.info("[AUTH] MFA PIN for user='{}' challengeId='{}' PIN='{}'", ch.username(), ch.challengeId(), ch.pin());
                    } else {
                        log.info("[AUTH] MFA challenge created for user='{}' challengeId='{}'", ch.username(), ch.challengeId());
                    }

                    return ResponseEntity.ok(new LoginResponse("MFA_REQUIRED", ch.challengeId()));
                })
                .switchIfEmpty(Mono.just(ResponseEntity.status(401).build()))
        );
    }

    @PostMapping("/mfa/verify")
    public Mono<ResponseEntity<VerifyMfaResponse>> verify(
            @Valid @RequestBody Mono<VerifyMfaRequest> body,
            org.springframework.http.server.reactive.ServerHttpRequest request
    ) {
        return body.flatMap(req -> {
            var ch = pinChallengeService.consume(req.challengeId());
            if (ch == null) {
                return Mono.just(ResponseEntity.status(401).build());
            }
            if (!ch.pin().equals(req.pin())) {
                return Mono.just(ResponseEntity.status(401).build());
            }

            return userService.loadUserWithRoles(ch.username())
                    .map(u -> {
                        String token = jwtService.generateAccessToken(u.username(), u.roles());

                        var ttl = cookieProps.sessionMaxAge(jwtProps);
                        String sessionId = sessions.createSession(u.username(), token, ttl);

                        var cookie = cookieProps.sessionCookie(BffSessionService.COOKIE_NAME, sessionId, ttl, request);

                        return ResponseEntity.ok()
                                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                                .body(new VerifyMfaResponse("AUTHENTICATED", u.username()));
                    })
                    .switchIfEmpty(Mono.just(ResponseEntity.status(401).build()));
        });
    }
}