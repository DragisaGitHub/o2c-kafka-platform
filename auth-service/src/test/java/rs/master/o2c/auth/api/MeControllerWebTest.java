package rs.master.o2c.auth.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import rs.master.o2c.auth.bff.BffCookieProperties;
import rs.master.o2c.auth.bff.BffCorsProperties;
import rs.master.o2c.auth.bff.BffSessionService;
import rs.master.o2c.auth.config.AuthJwtProperties;
import rs.master.o2c.auth.config.SecurityConfig;
import rs.master.o2c.auth.impl.JwtServiceImpl;
import rs.master.o2c.auth.security.BffSessionAuthenticationConverter;
import rs.master.o2c.auth.service.JwtService;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@WebFluxTest(controllers = MeController.class)
@Import({
        MeControllerWebTest.TestConfig.class,
        SecurityConfig.class,
        BffSessionService.class,
        BffSessionAuthenticationConverter.class
})
@TestPropertySource(properties = {
        "auth.jwt.secret=0123456789abcdef0123456789abcdef0123456789abcdef",
    "auth.jwt.expires-in-minutes=60",
    "bff.cookie.secure=false",
    "bff.cookie.same-site=Lax"
})
class MeControllerWebTest {

    @EnableConfigurationProperties({AuthJwtProperties.class, BffCookieProperties.class, BffCorsProperties.class})
    static class TestConfig {
        @Bean
        JwtService jwtService(AuthJwtProperties props) {
            return new JwtServiceImpl(props);
        }
    }

    @Autowired WebTestClient webTestClient;
    @Autowired BffSessionService sessions;
    @Autowired JwtService jwtService;

    @Test
    void apiMeUnauthenticatedReturns401WithoutWwwAuthenticateHeader() {
        webTestClient.get()
                .uri("/api/me")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().doesNotExist(HttpHeaders.WWW_AUTHENTICATE);
    }

    @Test
    void localShortcutLoginEndpointDoesNotExist() {
        webTestClient.post()
                .uri("/login")
                .exchange()
                .expectStatus().value(code -> assertThat(code).isIn(404, 405))
                .expectHeader().doesNotExist(HttpHeaders.SET_COOKIE)
                .expectHeader().doesNotExist(HttpHeaders.WWW_AUTHENTICATE);
    }

    @Test
    void apiMeReturnsUsernameAndRolesFromSecurityContext() {
        String token = jwtService.generateAccessToken("alice", List.of("ADMIN", "USER"));
        String sessionId = sessions.createSession("alice", token, Duration.ofMinutes(60));

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
                });
    }
}
