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
import org.springframework.test.web.reactive.server.WebTestClient;
import rs.master.o2c.auth.bff.BffCookieProperties;
import rs.master.o2c.auth.bff.BffCorsProperties;
import rs.master.o2c.auth.bff.BffSessionService;
import rs.master.o2c.auth.config.AuthJwtProperties;
import rs.master.o2c.auth.config.SecurityConfig;
import rs.master.o2c.auth.config.TotpProperties;
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

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = AdminUserTotpController.class)
@Import({
        AdminUserTotpControllerSecurityWebTest.TestConfig.class,
        SecurityConfig.class,
        BffSessionService.class,
        BffSessionAuthenticationConverter.class,
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
class AdminUserTotpControllerSecurityWebTest {

    @EnableConfigurationProperties({AuthJwtProperties.class, TotpProperties.class, BffCookieProperties.class, BffCorsProperties.class})
    static class TestConfig {
        @Bean
        JwtService jwtService(AuthJwtProperties props) {
            return new JwtServiceImpl(props);
        }
    }

    @Autowired WebTestClient webTestClient;
    @Autowired BffSessionService sessions;
    @Autowired JwtService jwtService;

    @MockBean TotpUserMfaService userMfa;

    private String sessionCookie(String username, List<String> roles) {
        String token = jwtService.generateAccessToken(username, roles);
        return sessions.createSession(username, token, Duration.ofMinutes(60));
    }

    @Test
    void adminCannotEnrollOrFetchQr() {
        String adminSession = sessionCookie("admin", List.of("ADMIN"));

        webTestClient.post()
                .uri("/api/admin/users/pera/mfa/totp/enroll")
                .cookie(BffSessionService.COOKIE_NAME, adminSession)
                .exchange()
                .expectStatus().isForbidden();

        webTestClient.get()
                .uri("/api/admin/users/pera/mfa/totp/qr?setupId=does-not-matter")
                .cookie(BffSessionService.COOKIE_NAME, adminSession)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void superAdminCanEnrollAndFetchQrPngWithNoStoreHeaders() {
        String saSession = sessionCookie("sa", List.of("SUPER_ADMIN"));

        when(userMfa.userExists(eq("pera"))).thenReturn(reactor.core.publisher.Mono.just(true));

        AtomicReference<String> setupId = new AtomicReference<>();

        webTestClient.post()
                .uri("/api/admin/users/pera/mfa/totp/enroll")
                .cookie(BffSessionService.COOKIE_NAME, saSession)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.setupId").value(v -> setupId.set(v.toString()))
                .jsonPath("$.username").isEqualTo("pera")
                .jsonPath("$.issuer").isEqualTo("O2C")
                .jsonPath("$.label").isEqualTo("O2C:pera")
                .jsonPath("$.expiresAt").exists()
                .jsonPath("$.secret").doesNotExist();

        webTestClient.get()
                .uri("/api/admin/users/pera/mfa/totp/qr?setupId=" + setupId.get())
                .cookie(BffSessionService.COOKIE_NAME, saSession)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.IMAGE_PNG)
                .expectHeader().value(HttpHeaders.CACHE_CONTROL,
                        v -> org.assertj.core.api.Assertions.assertThat(v).contains("no-store"))
                .expectHeader().valueEquals("Pragma", "no-cache")
                .expectBody(byte[].class)
                .consumeWith(res -> {
                    byte[] body = res.getResponseBody();
                    org.assertj.core.api.Assertions.assertThat(body).isNotNull();
                    org.assertj.core.api.Assertions.assertThat(body.length).isGreaterThan(100);
                });
    }
}
