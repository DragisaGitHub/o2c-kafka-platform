package rs.master.o2c.auth.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import rs.master.o2c.auth.api.dto.LoginRequest;
import rs.master.o2c.auth.api.dto.LoginResponse;
import rs.master.o2c.auth.api.dto.VerifyMfaRequest;
import rs.master.o2c.auth.api.dto.VerifyMfaResponse;
import rs.master.o2c.auth.service.InMemoryUsers;
import rs.master.o2c.auth.service.JwtService;
import rs.master.o2c.auth.service.PinChallengeService;

@RequiredArgsConstructor
@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final InMemoryUsers users;
    private final PinChallengeService pinChallengeService;
    private final JwtService jwtService;

    @PostMapping("/login")
    public Mono<ResponseEntity<LoginResponse>> login(@Valid @RequestBody Mono<LoginRequest> body) {
        return body.map(req -> {
            boolean ok = users.validCredentials(req.username(), req.password());
            if (!ok) {
                return ResponseEntity.status(401).build();
            }

            var ch = pinChallengeService.createChallenge(req.username());
            log.info("MFA PIN for user='{}' challengeId='{}' PIN='{}'", ch.username(), ch.challengeId(), ch.pin());

            return ResponseEntity.ok(new LoginResponse("MFA_REQUIRED", ch.challengeId(), ch.pin()));
        });
    }

    @PostMapping("/mfa/verify")
    public Mono<ResponseEntity<VerifyMfaResponse>> verify(@Valid @RequestBody Mono<VerifyMfaRequest> body) {
        return body.map(req -> {
            var ch = pinChallengeService.consume(req.challengeId());
            if (ch == null) {
                return ResponseEntity.status(401).build();
            }
            if (!ch.pin().equals(req.pin())) {
                return ResponseEntity.status(401).build();
            }

            String token = jwtService.generateAccessToken(ch.username());
            return ResponseEntity.ok(new VerifyMfaResponse("AUTHENTICATED", token));
        });
    }
}