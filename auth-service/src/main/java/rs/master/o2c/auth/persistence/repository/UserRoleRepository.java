package rs.master.o2c.auth.persistence.repository;

import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;

public interface UserRoleRepository {

    Mono<List<String>> findRolesByUserId(Long userId);

    Mono<Void> addRoles(Long userId, Collection<String> roles);

    Mono<Void> replaceRoles(Long userId, Collection<String> roles);

    Mono<Boolean> existsAnyUserWithRole(String role);
}
