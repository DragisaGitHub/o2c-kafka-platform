package rs.master.o2c.auth.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.context.TestPropertySource;
import reactor.core.publisher.Mono;
import rs.master.o2c.auth.api.dto.VerifyMfaRequest;
import rs.master.o2c.auth.api.dto.VerifyMfaResponse;
import rs.master.o2c.auth.bff.BffCookieProperties;
import rs.master.o2c.auth.bff.BffCorsProperties;
import rs.master.o2c.auth.bff.BffSessionService;
import rs.master.o2c.auth.config.AuthJwtProperties;
import rs.master.o2c.auth.config.TotpProperties;
import rs.master.o2c.auth.config.SecurityConfig;
import rs.master.o2c.auth.impl.InMemoryLoginChallengeService;
import rs.master.o2c.auth.impl.InMemoryTotpEnrollmentService;
import rs.master.o2c.auth.impl.JwtServiceImpl;
import rs.master.o2c.auth.impl.TotpCryptoServiceImpl;
import rs.master.o2c.auth.impl.TotpServiceImpl;
import rs.master.o2c.auth.security.BffSessionAuthenticationConverter;
import rs.master.o2c.auth.service.JwtService;
import rs.master.o2c.auth.service.LoginChallengeService;
import rs.master.o2c.auth.service.TotpCryptoService;
import rs.master.o2c.auth.service.TotpService;
import rs.master.o2c.auth.service.TotpUserMfaService;
import rs.master.o2c.auth.service.UserService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = {AuthController.class, MeController.class})
@Import({
        AuthControllerMfaWebTest.TestConfig.class,
        SecurityConfig.class,
        BffSessionService.class,
        BffSessionAuthenticationConverter.class,
    InMemoryLoginChallengeService.class,
    InMemoryTotpEnrollmentService.class,
    TotpServiceImpl.class,
    TotpCryptoServiceImpl.class
})
@TestPropertySource(properties = {
        "auth.jwt.secret=0123456789abcdef0123456789abcdef0123456789abcdef",
        "auth.jwt.expires-in-minutes=60",
    "mfa.totp.issuer=O2C",
    "mfa.totp.setup-ttl-seconds=600",
    "mfa.totp.login-challenge-ttl-seconds=180",
    "mfa.totp.encryption-key=test-key",
        "bff.cookie.secure=false",
        "bff.cookie.same-site=Lax"
})
class AuthControllerMfaWebTest {

    @EnableConfigurationProperties({AuthJwtProperties.class, TotpProperties.class, BffCookieProperties.class, BffCorsProperties.class})
    static class TestConfig {

        @Bean
        JwtService jwtService(AuthJwtProperties props) {
            return new JwtServiceImpl(props);
        }
    }

    @Autowired WebTestClient webTestClient;
    @Autowired LoginChallengeService loginChallengeService;
    @Autowired BffSessionService sessions;
    @Autowired JwtService jwtService;
    @Autowired TotpService totp;
    @Autowired TotpCryptoService crypto;

    @MockBean UserService userService;
    @MockBean TotpUserMfaService userMfa;

    @Test
    void mfaVerifySetsCookieAndApiMeReturnsUnprefixedRoles() {
        byte[] secret = "0123456789abcdefghij".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        byte[] secretEnc = crypto.encrypt(secret);

        var ch = loginChallengeService.createChallenge("alice");
        String code = totp.currentCode(secret);

        when(userService.loadUserWithRoles(eq("alice")))
                .thenReturn(Mono.just(new UserService.UserWithRoles("alice", List.of("ADMIN", "USER"))));

        when(userMfa.loadTotpMfa(eq("alice")))
            .thenReturn(Mono.just(new TotpUserMfaService.UserTotpMfa(true, secretEnc)));

        EntityExchangeResult<VerifyMfaResponse> result = webTestClient
                .post()
                .uri("/auth/mfa/verify")
            .bodyValue(new VerifyMfaRequest(ch.challengeId(), code))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists(HttpHeaders.SET_COOKIE)
                .expectBody(VerifyMfaResponse.class)
                .returnResult();

        String setCookie = result.getResponseHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotBlank();
        assertThat(setCookie).contains(BffSessionService.COOKIE_NAME + "=");
        assertThat(setCookie).contains("HttpOnly");
        assertThat(setCookie).contains("Path=/");
        assertThat(setCookie).contains("Max-Age=");

        String cookiePair = setCookie.split(";", 2)[0];
        String sessionId = cookiePair.substring((BffSessionService.COOKIE_NAME + "=").length());
        assertThat(sessionId).isNotBlank();

        var session = sessions.getValidSession(sessionId);
        assertThat(session).isNotNull();
        assertThat(session.username()).isEqualTo("alice");
        assertThat(session.accessToken()).isNotBlank();

        assertThat(result.getResponseBody()).isNotNull();
        assertThat(result.getResponseBody().status()).isEqualTo("AUTHENTICATED");
        assertThat(result.getResponseBody().username()).isEqualTo("alice");

        webTestClient.get()
                .uri("/api/me")
                .cookie(BffSessionService.COOKIE_NAME, sessionId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.username").isEqualTo("alice")
                .jsonPath("$.roles").isArray()
                .jsonPath("$.roles").value(v -> {
                    @SuppressWarnings("unchecked")
                    List<String> roles = (List<String>) v;
                    assertThat(roles).contains("ADMIN", "USER");
                    assertThat(roles).allMatch(r -> !r.startsWith("ROLE_"));
                });
    }
}
