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
import rs.master.o2c.auth.config.AuthMfaProperties;
import rs.master.o2c.auth.config.SecurityConfig;
import rs.master.o2c.auth.impl.InMemoryPinChallengeService;
import rs.master.o2c.auth.impl.JwtServiceImpl;
import rs.master.o2c.auth.security.BffSessionAuthenticationConverter;
import rs.master.o2c.auth.service.JwtService;
import rs.master.o2c.auth.service.PinChallengeService;
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
        InMemoryPinChallengeService.class
})
@TestPropertySource(properties = {
        "auth.jwt.secret=0123456789abcdef0123456789abcdef0123456789abcdef",
        "auth.jwt.expires-in-minutes=60",
        "auth.mfa.challenge-ttl-seconds=120",
        "bff.cookie.secure=false",
        "bff.cookie.same-site=Lax"
})
class AuthControllerMfaWebTest {

    @EnableConfigurationProperties({AuthJwtProperties.class, AuthMfaProperties.class, BffCookieProperties.class, BffCorsProperties.class})
    static class TestConfig {

        @Bean
        JwtService jwtService(AuthJwtProperties props) {
            return new JwtServiceImpl(props);
        }
    }

    @Autowired WebTestClient webTestClient;
    @Autowired PinChallengeService pinChallengeService;
    @Autowired BffSessionService sessions;
    @Autowired JwtService jwtService;

    @MockBean UserService userService;

    @Test
    void mfaVerifySetsCookieAndApiMeReturnsUnprefixedRoles() {
        var ch = pinChallengeService.createChallenge("alice");

        when(userService.loadUserWithRoles(eq("alice")))
                .thenReturn(Mono.just(new UserService.UserWithRoles("alice", List.of("ADMIN", "USER"))));

        EntityExchangeResult<VerifyMfaResponse> result = webTestClient
                .post()
                .uri("/auth/mfa/verify")
                .bodyValue(new VerifyMfaRequest(ch.challengeId(), ch.pin()))
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
