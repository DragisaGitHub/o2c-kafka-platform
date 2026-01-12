package rs.master.o2c.auth.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import rs.master.o2c.auth.api.admin.AdminUserTotpController;
import rs.master.o2c.auth.bff.BffCookieProperties;
import rs.master.o2c.auth.bff.BffCorsProperties;
import rs.master.o2c.auth.bff.BffSessionService;
import rs.master.o2c.auth.config.AuthJwtProperties;
import rs.master.o2c.auth.config.SecurityConfig;
import rs.master.o2c.auth.config.TotpProperties;
import rs.master.o2c.auth.impl.InMemoryLoginChallengeService;
import rs.master.o2c.auth.impl.InMemoryTotpEnrollmentService;
import rs.master.o2c.auth.impl.JwtServiceImpl;
import rs.master.o2c.auth.impl.TotpCryptoServiceImpl;
import rs.master.o2c.auth.impl.TotpServiceImpl;
import rs.master.o2c.auth.security.BffSessionAuthenticationConverter;
import rs.master.o2c.auth.service.JwtService;
import rs.master.o2c.auth.service.LoginChallengeService;
import rs.master.o2c.auth.service.TotpCryptoService;
import rs.master.o2c.auth.service.TotpEnrollmentService;
import rs.master.o2c.auth.service.TotpService;
import rs.master.o2c.auth.service.TotpUserMfaService;
import rs.master.o2c.auth.service.UserService;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = {
        AuthController.class,
        MeController.class,
        AdminUserTotpController.class
})
@Import({
        SecurityConfig.class,
        JwtServiceImpl.class,
        BffSessionService.class,
        BffSessionAuthenticationConverter.class,
        InMemoryLoginChallengeService.class,
        InMemoryTotpEnrollmentService.class,
        TotpServiceImpl.class,
        TotpCryptoServiceImpl.class
})
@EnableConfigurationProperties({AuthJwtProperties.class, TotpProperties.class, BffCookieProperties.class, BffCorsProperties.class})
@TestPropertySource(properties = {
        "auth.jwt.secret=01234567890123456789012345678901234567890123456789012345678901",
        "auth.jwt.expires-in-minutes=60",
        "mfa.totp.issuer=O2C",
        "mfa.totp.setup-ttl-seconds=600",
        "mfa.totp.login-challenge-ttl-seconds=180",
        "mfa.totp.encryption-key=test-key",
        "bff.cookie.secure=false",
        "bff.cookie.same-site=Lax",
        "bff.cors.allowed-origins=http://localhost:5173"
})
class TotpConfirmAndLoginWebTest {

    @Autowired WebTestClient webTestClient;
    @Autowired BffSessionService sessions;
    @Autowired JwtService jwtService;
    @Autowired TotpEnrollmentService enrollments;
    @Autowired TotpService totp;
    @Autowired TotpCryptoService crypto;

    @MockBean UserService userService;
    @MockBean TotpUserMfaService userMfa;

    @Test
    void superAdminEnrollsAndConfirmsThenUserCanLoginWithTotpAndGetsSessionCookie() {
        // SUPER_ADMIN session for calling /api/admin/**
        String saToken = jwtService.generateAccessToken("sa", List.of("SUPER_ADMIN"));
        String saSessionId = sessions.createSession("sa", saToken, Duration.ofMinutes(60));

        // In-memory behavior for the mocked MFA store
        Map<String, byte[]> secrets = new ConcurrentHashMap<>();

        when(userMfa.userExists(eq("pera"))).thenReturn(Mono.just(true));
        when(userMfa.enableTotp(eq("pera"), any(byte[].class), any()))
                .thenAnswer(inv -> {
                    byte[] enc = inv.getArgument(1);
                    secrets.put("pera", enc);
                    return Mono.empty();
                });
        when(userMfa.loadTotpMfa(eq("pera")))
                .thenAnswer(inv -> {
                    byte[] enc = secrets.get("pera");
                    if (enc == null) {
                        return Mono.empty();
                    }
                    return Mono.just(new TotpUserMfaService.UserTotpMfa(true, enc));
                });

        // User creds/roles for login + post-MFA session
        when(userService.validateCredentials(eq("pera"), eq("pw")))
                .thenReturn(Mono.just(new UserService.UserWithRoles("pera", List.of("USER"))));
        when(userService.loadUserWithRoles(eq("pera")))
                .thenReturn(Mono.just(new UserService.UserWithRoles("pera", List.of("USER"))));

        AtomicReference<String> setupId = new AtomicReference<>();

        webTestClient.post()
                .uri("/api/admin/users/pera/mfa/totp/enroll")
                .cookie(BffSessionService.COOKIE_NAME, saSessionId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.setupId").value(v -> setupId.set(v.toString()));

        var session = enrollments.getValidSetup(setupId.get());
        assertThat(session).isNotNull();
        assertThat(session.username()).isEqualTo("pera");

        String confirmCode = totp.currentCode(session.secret());

        webTestClient.post()
                .uri("/api/admin/users/pera/mfa/totp/confirm")
                .cookie(BffSessionService.COOKIE_NAME, saSessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"setupId\":\"" + setupId.get() + "\",\"code\":\"" + confirmCode + "\"}")
                .exchange()
                .expectStatus().isNoContent();

        AtomicReference<String> challengeId = new AtomicReference<>();

        webTestClient.post()
                .uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"pera\",\"password\":\"pw\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("MFA_REQUIRED")
                .jsonPath("$.challengeId").value(v -> challengeId.set(v.toString()));

        String loginCode = totp.currentCode(crypto.decrypt(secrets.get("pera")));

        EntityExchangeResult<byte[]> verify = webTestClient.post()
                .uri("/auth/mfa/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"challengeId\":\"" + challengeId.get() + "\",\"pin\":\"" + loginCode + "\"}")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists(HttpHeaders.SET_COOKIE)
                .expectBody()
                .jsonPath("$.status").isEqualTo("AUTHENTICATED")
                .jsonPath("$.username").isEqualTo("pera")
                .returnResult();

        String setCookie = verify.getResponseHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains(BffSessionService.COOKIE_NAME + "=");
        String sessionId = setCookie.split(";", 2)[0].split("=", 2)[1];

        webTestClient.get()
                .uri("/api/me")
                .cookie(BffSessionService.COOKIE_NAME, sessionId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.username").isEqualTo("pera")
                .jsonPath("$.roles[0]").isEqualTo("USER");
    }
}
