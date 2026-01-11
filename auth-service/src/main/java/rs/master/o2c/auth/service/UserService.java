package rs.master.o2c.auth.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import rs.master.o2c.auth.persistence.entity.UserEntity;
import rs.master.o2c.auth.persistence.repository.UserRepository;
import rs.master.o2c.auth.persistence.repository.UserRoleRepository;

import java.util.List;

@Service
public class UserService {

    private final UserRepository users;
    private final UserRoleRepository roles;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository users, UserRoleRepository roles, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
    }

    public record UserWithRoles(String username, List<String> roles) {}

    public Mono<UserWithRoles> validateCredentials(String username, String rawPassword) {
        if (username == null || username.isBlank() || rawPassword == null) {
            return Mono.empty();
        }

        String normalized = username.trim();

        return users.findByUsername(normalized)
                .filter(UserEntity::enabled)
                .filter(u -> u.passwordHash() != null && passwordEncoder.matches(rawPassword, u.passwordHash()))
                .flatMap(u -> roles.findRolesByUserId(u.id())
                        .map(rs -> new UserWithRoles(u.username(), rs))
                );
    }

    public Mono<UserWithRoles> loadUserWithRoles(String username) {
        if (username == null || username.isBlank()) {
            return Mono.empty();
        }

        String normalized = username.trim();

        return users.findByUsername(normalized)
                .filter(UserEntity::enabled)
                .flatMap(u -> roles.findRolesByUserId(u.id())
                        .map(rs -> new UserWithRoles(u.username(), rs))
                );
    }
}
