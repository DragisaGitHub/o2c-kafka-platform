package rs.master.o2c.auth.api.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.http.MediaType;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import rs.master.o2c.auth.service.AdminUserService;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.time.Instant;

@RestController
@RequestMapping("/api/admin")
public class AdminUsersController {

    private static final Set<String> ALLOWED_ROLES = Set.of("USER", "ADMIN", "SUPER_ADMIN");

    private final AdminUserService adminUsers;

    public AdminUsersController(AdminUserService adminUsers) {
        this.adminUsers = adminUsers;
    }

    public record CreateUserRequest(
            @NotBlank String username,
            @NotBlank String password,
            @NotEmpty Collection<String> roles
    ) {}

    public record UserResponse(
            String username,
            List<String> roles,
            boolean enabled,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record SetEnabledRequest(Boolean enabled) {}

    public record SetRolesRequest(@NotEmpty Collection<String> roles) {}

    public record ResetPasswordRequest(@NotBlank String password) {}

    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @GetMapping(value = "/users", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<List<UserResponse>> listUsers() {
        return adminUsers.listUsers()
                .map(AdminUsersController::toResponse)
                .collectList();
    }

    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @GetMapping(value = "/users/{username}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<UserResponse> getUser(@PathVariable String username) {
        String normalized = normalizeUsername(username);

        return adminUsers.getUser(normalized)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                .map(AdminUsersController::toResponse);
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping(value = "/users", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<UserResponse>> createUser(@RequestBody Mono<CreateUserRequest> body) {
        return body.flatMap(req -> {
            String username = normalizeUsername(req.username());
            String password = requireNonBlank(req.password());
            List<String> normalizedRoles = normalizeRoles(req.roles());

            return adminUsers.createUser(username, password, normalizedRoles)
                    .map(created -> ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created)))
                    .onErrorResume(DuplicateKeyException.class, e -> Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).build()));
        });
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PatchMapping(value = "/users/{username}/enabled", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<UserResponse> setEnabled(@PathVariable String username, @RequestBody Mono<SetEnabledRequest> body) {
        String normalized = normalizeUsername(username);
        return body.flatMap(req -> {
            if (req == null || req.enabled() == null) {
                return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "enabled is required"));
            }

            return adminUsers.setEnabled(normalized, req.enabled())
                    .onErrorMap(AdminUsersController::mapNotFound)
                    .map(AdminUsersController::toResponse);
        });
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PatchMapping(value = "/users/{username}/roles", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<UserResponse> setRoles(@PathVariable String username, @RequestBody Mono<SetRolesRequest> body) {
        String normalized = normalizeUsername(username);
        return body.flatMap(req -> {
            List<String> normalizedRoles = normalizeRoles(req == null ? null : req.roles());
            return adminUsers.setRoles(normalized, normalizedRoles)
                    .onErrorMap(AdminUsersController::mapNotFound)
                    .map(AdminUsersController::toResponse);
        });
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping(value = "/users/{username}/password")
    public Mono<ResponseEntity<Void>> resetPassword(@PathVariable String username, @RequestBody Mono<ResetPasswordRequest> body) {
        String normalized = normalizeUsername(username);
        return body.flatMap(req -> {
            String password = requireNonBlank(req == null ? null : req.password());
            return adminUsers.resetPassword(normalized, password)
                    .onErrorMap(AdminUsersController::mapNotFound)
                    .thenReturn(ResponseEntity.noContent().build());
        });
    }

    private static UserResponse toResponse(AdminUserService.AdminUserDetails u) {
        return new UserResponse(u.username(), u.roles(), u.enabled(), u.createdAt(), u.updatedAt());
    }

    private static String normalizeUsername(String username) {
        String u = username == null ? null : username.trim();
        if (u == null || u.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "username is required");
        }
        return u;
    }

    private static String requireNonBlank(String value) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "password" + " is required");
        }
        return value;
    }

    private static List<String> normalizeRoles(Collection<String> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "roles are required");
        }

        List<String> normalizedRoles = roles.stream()
                .filter(r -> r != null && !r.isBlank())
                .map(r -> r.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();

        if (normalizedRoles.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "roles are required");
        }
        if (!ALLOWED_ROLES.containsAll(normalizedRoles)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "roles contain invalid values");
        }

        return normalizedRoles;
    }

    private static Throwable mapNotFound(Throwable e) {
        if (e instanceof IllegalStateException ise && "NOT_FOUND".equals(ise.getMessage())) {
            return new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return e;
    }
}
