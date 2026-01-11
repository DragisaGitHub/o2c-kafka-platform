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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import rs.master.o2c.auth.bff.BffCookieProperties;
import rs.master.o2c.auth.bff.BffCorsProperties;
import rs.master.o2c.auth.bff.BffSessionService;
import rs.master.o2c.auth.config.AuthJwtProperties;
import rs.master.o2c.auth.config.SecurityConfig;
import rs.master.o2c.auth.impl.JwtServiceImpl;
import rs.master.o2c.auth.security.BffSessionAuthenticationConverter;
import rs.master.o2c.auth.service.AdminUserService;
import rs.master.o2c.auth.service.JwtService;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = AdminUsersController.class)
@Import({
        AdminUsersControllerSecurityWebTest.TestConfig.class,
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
class AdminUsersControllerSecurityWebTest {

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

    @MockBean AdminUserService adminUserService;

        private String sessionCookie(String username, List<String> roles) {
        String token = jwtService.generateAccessToken(username, roles);
        return sessions.createSession(username, token, Duration.ofMinutes(60));
        }

    @Test
        void unauthenticatedAdminEndpointsReturn401WithoutWwwAuthenticate() {
        webTestClient.get()
            .uri("/api/admin/users")
            .exchange()
            .expectStatus().isUnauthorized()
            .expectHeader().doesNotExist(HttpHeaders.WWW_AUTHENTICATE);
        }

        @Test
        void adminCanReadButCannotMutate() {
        String adminSession = sessionCookie("admin", List.of("ADMIN"));

        when(adminUserService.listUsers()).thenReturn(Flux.just(
            new AdminUserService.AdminUserDetails(1L, "alice", List.of("USER"), true, Instant.now(), null)
        ));

        webTestClient.get()
            .uri("/api/admin/users")
            .cookie(BffSessionService.COOKIE_NAME, adminSession)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$[0].username").isEqualTo("alice")
            .jsonPath("$[0].roles[0]").isEqualTo("USER");

        webTestClient.post()
            .uri("/api/admin/users")
            .cookie(BffSessionService.COOKIE_NAME, adminSession)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"username\":\"newuser\",\"password\":\"pw\",\"roles\":[\"USER\"]}")
            .exchange()
            .expectStatus().isForbidden();

        webTestClient.patch()
            .uri("/api/admin/users/alice/enabled")
            .cookie(BffSessionService.COOKIE_NAME, adminSession)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"enabled\":false}")
            .exchange()
            .expectStatus().isForbidden();

        webTestClient.patch()
            .uri("/api/admin/users/alice/roles")
            .cookie(BffSessionService.COOKIE_NAME, adminSession)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"roles\":[\"ADMIN\"]}")
            .exchange()
            .expectStatus().isForbidden();

        verify(adminUserService).listUsers();
        }

        @Test
        void superAdminCanMutate() {
        String saSession = sessionCookie("sa", List.of("SUPER_ADMIN"));

        when(adminUserService.createUser(eq("newuser"), eq("pw"), any()))
            .thenReturn(Mono.just(new AdminUserService.AdminUserDetails(2L, "newuser", List.of("USER"), true, Instant.now(), null)));
        when(adminUserService.setEnabled(eq("newuser"), eq(false)))
            .thenReturn(Mono.just(new AdminUserService.AdminUserDetails(2L, "newuser", List.of("USER"), false, Instant.now(), Instant.now())));
        when(adminUserService.setRoles(eq("newuser"), any()))
            .thenReturn(Mono.just(new AdminUserService.AdminUserDetails(2L, "newuser", List.of("ADMIN"), false, Instant.now(), Instant.now())));

        webTestClient.post()
            .uri("/api/admin/users")
            .cookie(BffSessionService.COOKIE_NAME, saSession)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"username\":\"newuser\",\"password\":\"pw\",\"roles\":[\"USER\"]}")
            .exchange()
            .expectStatus().isCreated();

        webTestClient.patch()
            .uri("/api/admin/users/newuser/enabled")
            .cookie(BffSessionService.COOKIE_NAME, saSession)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"enabled\":false}")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.enabled").isEqualTo(false);

        webTestClient.patch()
            .uri("/api/admin/users/newuser/roles")
            .cookie(BffSessionService.COOKIE_NAME, saSession)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"roles\":[\"ADMIN\"]}")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.roles[0]").isEqualTo("ADMIN");

        verify(adminUserService).createUser(eq("newuser"), eq("pw"), any());
        verify(adminUserService).setEnabled(eq("newuser"), eq(false));
        verify(adminUserService).setRoles(eq("newuser"), any());
    }
}
