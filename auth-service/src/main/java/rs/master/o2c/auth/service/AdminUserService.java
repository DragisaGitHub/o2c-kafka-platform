package rs.master.o2c.auth.service;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import rs.master.o2c.auth.persistence.entity.UserEntity;
import rs.master.o2c.auth.persistence.repository.UserRepository;
import rs.master.o2c.auth.persistence.repository.UserRoleRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Service
public class AdminUserService {

    private final UserRepository users;
    private final UserRoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final TransactionalOperator tx;

    public AdminUserService(
            UserRepository users,
            UserRoleRepository roles,
            PasswordEncoder passwordEncoder,
            TransactionalOperator tx
    ) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.tx = tx;
    }

    public Mono<Boolean> hasAnySuperAdmin() {
        return roles.existsAnyUserWithRole("SUPER_ADMIN");
    }

    public record AdminUserDetails(
            Long id,
            String username,
            List<String> roles,
            boolean enabled,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public Flux<AdminUserDetails> listUsers() {
        return users.findAll()
                .flatMap(this::toDetails);
    }

    public Mono<AdminUserDetails> getUser(String username) {
        if (username == null || username.isBlank()) {
            return Mono.error(new IllegalArgumentException("username is required"));
        }

        String normalized = username.trim();
        return users.findByUsername(normalized)
                .switchIfEmpty(Mono.empty())
                .flatMap(this::toDetails);
    }

    public Mono<AdminUserDetails> createUser(String username, String rawPassword, Collection<String> roleNames) {
        if (username == null || username.isBlank()) {
            return Mono.error(new IllegalArgumentException("username is required"));
        }
        if (rawPassword == null || rawPassword.isBlank()) {
            return Mono.error(new IllegalArgumentException("password is required"));
        }
        if (roleNames == null || roleNames.isEmpty()) {
            return Mono.error(new IllegalArgumentException("roles are required"));
        }

        String normalizedUsername = username.trim();
        List<String> rolesList = roleNames.stream().toList();

        Instant now = Instant.now();

        return tx.transactional(
                users.save(new UserEntity(
                                null,
                                normalizedUsername,
                                passwordEncoder.encode(rawPassword),
                                true,
                                now,
                                null
                        ))
                        .flatMap(saved -> roles.addRoles(saved.id(), rolesList)
                                .thenReturn(new AdminUserDetails(saved.id(), saved.username(), rolesList, saved.enabled(), saved.createdAt(), saved.updatedAt()))
                        )
        );
    }

    public Mono<AdminUserDetails> setEnabled(String username, boolean enabled) {
        if (username == null || username.isBlank()) {
            return Mono.error(new IllegalArgumentException("username is required"));
        }

        String normalized = username.trim();
        Instant now = Instant.now();

        return tx.transactional(
                users.findByUsername(normalized)
                        .switchIfEmpty(Mono.error(new IllegalStateException("NOT_FOUND")))
                        .flatMap(u -> {
                            u.setEnabled(enabled);
                            u.setUpdatedAt(now);
                            return users.save(u);
                        })
                        .flatMap(this::toDetails)
        );
    }

    public Mono<AdminUserDetails> setRoles(String username, Collection<String> roleNames) {
        if (username == null || username.isBlank()) {
            return Mono.error(new IllegalArgumentException("username is required"));
        }
        if (roleNames == null || roleNames.isEmpty()) {
            return Mono.error(new IllegalArgumentException("roles are required"));
        }

        String normalized = username.trim();
        List<String> rolesList = roleNames.stream().toList();
        Instant now = Instant.now();

        return tx.transactional(
                users.findByUsername(normalized)
                        .switchIfEmpty(Mono.error(new IllegalStateException("NOT_FOUND")))
                        .flatMap(u -> {
                            u.setUpdatedAt(now);
                            return users.save(u)
                                    .then(roles.replaceRoles(u.id(), rolesList))
                                    .then(users.findById(u.id()));
                        })
                        .flatMap(this::toDetails)
        );
    }

    public Mono<Void> resetPassword(String username, String rawPassword) {
        if (username == null || username.isBlank()) {
            return Mono.error(new IllegalArgumentException("username is required"));
        }
        if (rawPassword == null || rawPassword.isBlank()) {
            return Mono.error(new IllegalArgumentException("password is required"));
        }

        String normalized = username.trim();
        Instant now = Instant.now();

        return tx.transactional(
                users.findByUsername(normalized)
                        .switchIfEmpty(Mono.error(new IllegalStateException("NOT_FOUND")))
                        .flatMap(u -> {
                            u.setPasswordHash(passwordEncoder.encode(rawPassword));
                            u.setUpdatedAt(now);
                            return users.save(u);
                        })
                        .then()
        );
    }

    private Mono<AdminUserDetails> toDetails(UserEntity u) {
        if (u == null) {
            return Mono.empty();
        }

        return roles.findRolesByUserId(u.id())
                .map(rs -> new AdminUserDetails(u.id(), u.username(), rs, u.enabled(), u.createdAt(), u.updatedAt()));
    }
}
