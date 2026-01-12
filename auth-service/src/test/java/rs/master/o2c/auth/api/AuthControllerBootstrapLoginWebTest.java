package rs.master.o2c.auth.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import rs.master.o2c.auth.api.dto.LoginResponse;
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
import rs.master.o2c.auth.service.TotpCryptoService;
import rs.master.o2c.auth.service.TotpEnrollmentService;
import rs.master.o2c.auth.service.TotpService;
import rs.master.o2c.auth.service.TotpUserMfaService;
import rs.master.o2c.auth.service.UserService;

import java.util.Map;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = {AuthController.class, MeController.class})
@Import({
        AuthControllerBootstrapLoginWebTest.TestConfig.class,
        SecurityConfig.class,
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
        "bff.cookie.same-site=Lax"
})
class AuthControllerBootstrapLoginWebTest {

    static class TestConfig {
        @Bean
        JwtService jwtService(AuthJwtProperties props) {
            return new JwtServiceImpl(props);
        }
    }

    @Autowired WebTestClient webTestClient;

        @Autowired TotpEnrollmentService enrollments;

    @MockBean UserService userService;
    @MockBean TotpUserMfaService userMfa;

    // Only needed to satisfy the controller's constructor via @Import
    @Autowired TotpService totp;
    @Autowired TotpCryptoService crypto;

    @Test
    void loginWithoutTotpEnrollmentEnrollsInLoginAndThenRequiresMfaNextTime() {
        Map<String, byte[]> secrets = new ConcurrentHashMap<>();

        when(userService.validateCredentials(eq("sa"), eq("pw")))
                .thenReturn(Mono.just(new UserService.UserWithRoles("sa", List.of("SUPER_ADMIN"))));

        when(userService.loadUserWithRoles(eq("sa")))
                .thenReturn(Mono.just(new UserService.UserWithRoles("sa", List.of("SUPER_ADMIN"))));

        // Fresh DB bootstrap: missing user_mfa row -> ENROLL_REQUIRED
        when(userMfa.loadTotpMfa(eq("sa")))
                .thenAnswer(inv -> {
                    byte[] enc = secrets.get("sa");
                    if (enc == null) {
                        return Mono.empty();
                    }
                    return Mono.just(new TotpUserMfaService.UserTotpMfa(true, enc));
                });

        when(userMfa.enableTotp(eq("sa"), any(byte[].class), any()))
                .thenAnswer(inv -> {
                    byte[] enc = inv.getArgument(1);
                    secrets.put("sa", enc);
                    return Mono.empty();
                });

        EntityExchangeResult<LoginResponse> login = webTestClient.post()
                .uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"sa\",\"password\":\"pw\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody(LoginResponse.class)
                .returnResult();

        assertThat(login.getResponseBody()).isNotNull();
        assertThat(login.getResponseBody().status()).isEqualTo("ENROLL_REQUIRED");
        assertThat(login.getResponseBody().challengeId()).isNotBlank();

        String setupId = login.getResponseBody().challengeId();
        var setup = enrollments.getValidSetup(setupId);
        assertThat(setup).isNotNull();
        String code = totp.currentCode(setup.secret());

        EntityExchangeResult<byte[]> confirm = webTestClient.post()
                .uri("/auth/mfa/totp/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"setupId\":\"" + setupId + "\",\"code\":\"" + code + "\"}")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists(HttpHeaders.SET_COOKIE)
                .expectBody()
                .jsonPath("$.status").isEqualTo("AUTHENTICATED")
                .jsonPath("$.username").isEqualTo("sa")
                .returnResult();

        String setCookie = confirm.getResponseHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotBlank();
        assertThat(setCookie).contains(BffSessionService.COOKIE_NAME + "=");

        String cookiePair = setCookie.split(";", 2)[0];
        String sessionId = cookiePair.substring((BffSessionService.COOKIE_NAME + "=").length());
        assertThat(sessionId).isNotBlank();

        webTestClient.get()
                .uri("/api/me")
                .cookie(BffSessionService.COOKIE_NAME, sessionId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.username").isEqualTo("sa")
                .jsonPath("$.roles").isArray()
                .jsonPath("$.roles[0]").isEqualTo("SUPER_ADMIN");

        webTestClient.post()
                .uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"sa\",\"password\":\"pw\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("MFA_REQUIRED")
                .jsonPath("$.challengeId").isNotEmpty();
    }
}
