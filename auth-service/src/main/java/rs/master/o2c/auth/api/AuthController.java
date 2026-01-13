package rs.master.o2c.auth.api;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import rs.master.o2c.auth.api.dto.*;
import rs.master.o2c.auth.bff.BffCookieProperties;
import rs.master.o2c.auth.bff.BffSessionService;
import rs.master.o2c.auth.config.AuthJwtProperties;
import rs.master.o2c.auth.service.*;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;

@RequiredArgsConstructor
@RestController
@RequestMapping("/auth")
@Slf4j
public class AuthController {

    private final UserService userService;
    private final LoginChallengeService loginChallengeService;
    private final JwtService jwtService;
    private final AuthJwtProperties jwtProps;
    private final BffSessionService sessions;
    private final BffCookieProperties cookieProps;
    private final TotpUserMfaService userMfa;
    private final TotpCryptoService crypto;
    private final TotpService totp;
    private final TotpEnrollmentService enrollments;

    @PostMapping("/login")
    public Mono<ResponseEntity<LoginResponse>> login(
            @Valid @RequestBody Mono<LoginRequest> body
    ) {
        return body.flatMap(req -> userService.validateCredentials(req.username(), req.password())
                .flatMap(u -> userMfa.loadTotpMfa(u.username())
                        .defaultIfEmpty(new TotpUserMfaService.UserTotpMfa(false, null))
                        .map(mfa -> {
                            boolean enrolled = mfa != null && mfa.enabled() && mfa.secretEnc() != null && mfa.secretEnc().length > 0;
                            if (enrolled) {
                                var ch = loginChallengeService.createChallenge(u.username());
                                log.info("[AUTH] MFA challenge created for user='{}' challengeId='{}'", ch.username(), ch.challengeId());
                                return ResponseEntity.ok(new LoginResponse("MFA_REQUIRED", ch.challengeId()));
                            }

                            // Not enrolled: require in-login TOTP enrollment.
                            var setup = enrollments.createSetup(u.username());
                            log.info("[AUTH] TOTP enrollment required for user='{}' setupId='{}'", setup.username(), setup.setupId());
                            // Reuse the existing response shape: challengeId carries setupId for ENROLL_REQUIRED.
                            return ResponseEntity.ok(new LoginResponse("ENROLL_REQUIRED", setup.setupId()));
                        })
                )
                .switchIfEmpty(Mono.just(ResponseEntity.status(401).build())));
    }

    @GetMapping(value = "/mfa/totp/qr", produces = MediaType.IMAGE_PNG_VALUE)
    public Mono<ResponseEntity<byte[]>> totpQr(@RequestParam(name = "setupId") String setupId) {
        String id = requireNonBlank(setupId, "setupId");

        var session = enrollments.getValidSetup(id);
        if (session == null) {
            return Mono.just(ResponseEntity.status(404).build());
        }

        String uri = totp.buildOtpAuthUri(session.issuer(), session.username(), session.secret());
        byte[] png = renderQrPng(uri);

        return Mono.just(ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, max-age=0")
                .header("Pragma", "no-cache")
                .body(png));
    }

    @PostMapping(value = "/mfa/totp/confirm", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<VerifyMfaResponse>> confirmTotpEnrollment(
            @Valid @RequestBody Mono<ConfirmTotpEnrollmentRequest> body,
            org.springframework.http.server.reactive.ServerHttpRequest request
    ) {
        return body.flatMap(req -> {
            String setupId = requireNonBlank(req == null ? null : req.setupId(), "setupId");
            String code = requireNonBlank(req == null ? null : req.code(), "code");

            var session = enrollments.getValidSetup(setupId);
            if (session == null) {
                return Mono.just(ResponseEntity.status(404).build());
            }

            if (!totp.isValidCode(session.secret(), code)) {
                return Mono.just(ResponseEntity.status(401).build());
            }

            byte[] secretEnc = crypto.encrypt(session.secret());
            Instant now = Instant.now();

            return userMfa.enableTotp(session.username(), secretEnc, now)
                    .onErrorResume(e -> {
                        if (e instanceof IllegalStateException ise && "NOT_FOUND".equals(ise.getMessage())) {
                            return Mono.error(new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
                        }
                        return Mono.error(e);
                    })
                    .then(userService.loadUserWithRoles(session.username()))
                    .switchIfEmpty(Mono.just(new UserService.UserWithRoles(session.username(), java.util.List.of())))
                    .map(u -> {
                        enrollments.consumeSetup(setupId);

                        String token = jwtService.generateAccessToken(u.username(), u.roles());
                        var ttl = cookieProps.sessionMaxAge(jwtProps);
                        String sessionId = sessions.createSession(u.username(), token, ttl);
                        var cookie = cookieProps.sessionCookie(BffSessionService.COOKIE_NAME, sessionId, ttl, request);

                        return ResponseEntity.ok()
                                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                                .body(new VerifyMfaResponse("AUTHENTICATED", u.username()));
                    });
        });
    }

    @PostMapping("/mfa/verify")
    public Mono<ResponseEntity<VerifyMfaResponse>> verify(
            @Valid @RequestBody Mono<VerifyMfaRequest> body,
            org.springframework.http.server.reactive.ServerHttpRequest request
    ) {
        return body.flatMap(req -> {
            var ch = loginChallengeService.consume(req.challengeId());
            if (ch == null || ch.username() == null || ch.username().isBlank()) {
                return Mono.just(ResponseEntity.status(401).build());
            }

            String code = req.pin();

            return userService.loadUserWithRoles(ch.username())
                    .flatMap(u -> userMfa.loadTotpMfa(u.username())
                            .flatMap(mfa -> {
                                if (mfa == null || !mfa.enabled() || mfa.secretEnc() == null || mfa.secretEnc().length == 0) {
                                    // MFA verify should not be used when MFA is not enabled for the user.
                                    return Mono.error(new ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "MFA is not enabled for this user"));
                                }

                                byte[] secret;
                                try {
                                    secret = crypto.decrypt(mfa.secretEnc());
                                } catch (Exception e) {
                                    return Mono.just(ResponseEntity.status(401).<VerifyMfaResponse>build());
                                }

                                if (!totp.isValidCode(secret, code)) {
                                    return Mono.just(ResponseEntity.status(401).<VerifyMfaResponse>build());
                                }

                                String token = jwtService.generateAccessToken(u.username(), u.roles());

                                var ttl = cookieProps.sessionMaxAge(jwtProps);
                                String sessionId = sessions.createSession(u.username(), token, ttl);

                                var cookie = cookieProps.sessionCookie(BffSessionService.COOKIE_NAME, sessionId, ttl, request);

                                return Mono.just(ResponseEntity.ok()
                                        .header(HttpHeaders.SET_COOKIE, cookie.toString())
                                        .body(new VerifyMfaResponse("AUTHENTICATED", u.username())));
                            })
                                .switchIfEmpty(Mono.just(ResponseEntity.status(401).build()))
                    )
                            .switchIfEmpty(Mono.just(ResponseEntity.status(401).build()));
        });
    }

    private static String requireNonBlank(String value, String field) {
        String v = value == null ? null : value.trim();
        if (v == null || v.isBlank()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, field + " is required");
        }
        return v;
    }

    private static byte[] renderQrPng(String content) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, 256, 256);
            var image = MatrixToImageWriter.toBufferedImage(matrix);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", baos);
            return baos.toByteArray();
        } catch (WriterException | IOException e) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "QR generation failed", e);
        }
    }
}