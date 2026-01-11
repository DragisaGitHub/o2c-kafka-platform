package rs.master.o2c.auth.api.admin;

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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import rs.master.o2c.auth.api.AuthController;
import rs.master.o2c.auth.api.MeController;
import rs.master.o2c.auth.bff.BffCookieProperties;
import rs.master.o2c.auth.bff.BffCorsProperties;
import rs.master.o2c.auth.bff.BffSessionService;
import rs.master.o2c.auth.config.AuthJwtProperties;
import rs.master.o2c.auth.config.SecurityConfig;
import rs.master.o2c.auth.impl.JwtServiceImpl;
import rs.master.o2c.auth.security.BffSessionAuthenticationConverter;
import rs.master.o2c.auth.service.AdminUserService;
import rs.master.o2c.auth.service.JwtService;
import rs.master.o2c.auth.service.PinChallengeService;
import rs.master.o2c.auth.service.UserService;
import rs.master.o2c.auth.service.model.PinChallenge;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = {
        AuthController.class,
        MeController.class,
        AdminUsersController.class
})
@Import({SecurityConfig.class, JwtServiceImpl.class, BffSessionAuthenticationConverter.class, AdminUsersControllerMfaFlowWebTest.TestConfig.class})
@EnableConfigurationProperties({AuthJwtProperties.class, BffCookieProperties.class, BffCorsProperties.class})
@TestPropertySource(properties = {
    "auth.jwt.secret=01234567890123456789012345678901234567890123456789012345678901",
        "auth.jwt.expires-in-minutes=60",
        "bff.cookie.secure=false",
        "bff.cors.allowed-origins=http://localhost:5173"
})
class AdminUsersControllerMfaFlowWebTest {

    @Autowired WebTestClient webTestClient;

    @MockBean UserService userService;
    @MockBean AdminUserService adminUserService;

    @Test
    void mfaLoginThenSuperAdminCanAccessAdminApi() {
        when(userService.validateCredentials(eq("sa"), eq("pw")))
                .thenReturn(Mono.just(new UserService.UserWithRoles("sa", List.of("SUPER_ADMIN"))));
        when(userService.loadUserWithRoles(eq("sa")))
                .thenReturn(Mono.just(new UserService.UserWithRoles("sa", List.of("SUPER_ADMIN"))));

        AtomicReference<String> challengeId = new AtomicReference<>();

        webTestClient.post()
                .uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"sa\",\"password\":\"pw\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("MFA_REQUIRED")
                .jsonPath("$.challengeId").value(v -> challengeId.set(v.toString()));

        EntityExchangeResult<byte[]> verifyResult = webTestClient.post()
                .uri("/auth/mfa/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"challengeId\":\"" + challengeId.get() + "\",\"pin\":\"12345\"}")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists(HttpHeaders.SET_COOKIE)
                .expectBody()
                .jsonPath("$.status").isEqualTo("AUTHENTICATED")
                .jsonPath("$.username").isEqualTo("sa")
                .returnResult();

        String setCookie = verifyResult.getResponseHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains(BffSessionService.COOKIE_NAME + "=");
        String sessionId = setCookie.split(";", 2)[0].split("=", 2)[1];

        webTestClient.get()
                .uri("/api/me")
                .cookie(BffSessionService.COOKIE_NAME, sessionId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.username").isEqualTo("sa")
                .jsonPath("$.roles[0]").isEqualTo("SUPER_ADMIN");

        when(adminUserService.listUsers()).thenReturn(Flux.just(
                new AdminUserService.AdminUserDetails(1L, "alice", List.of("USER"), true, Instant.now(), null)
        ));

        webTestClient.get()
                .uri("/api/admin/users")
                .cookie(BffSessionService.COOKIE_NAME, sessionId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].username").isEqualTo("alice");
    }

    static class TestConfig {
        @Bean
        BffSessionService bffSessionService() {
            return new BffSessionService();
        }

        @Bean
        JwtService jwtService(AuthJwtProperties props) {
            return new JwtServiceImpl(props);
        }

        @Bean
        PinChallengeService pinChallengeService() {
            return new FixedPinChallengeService();
        }
    }

    static class FixedPinChallengeService implements PinChallengeService {
        private final Map<String, PinChallenge> challenges = new ConcurrentHashMap<>();

        @Override
        public PinChallenge createChallenge(String username) {
            String id = UUID.randomUUID().toString();
            PinChallenge ch = new PinChallenge(id, username, "12345", Instant.now(), Instant.now().plus(Duration.ofMinutes(5)));
            challenges.put(id, ch);
            return ch;
        }

        @Override
        public PinChallenge consume(String challengeId) {
            return challenges.remove(challengeId);
        }
    }
}
