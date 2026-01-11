package rs.master.o2c.auth.persistence.repository;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;

@Repository
public class UserRoleRepositoryImpl implements UserRoleRepository {

    private final DatabaseClient db;

    public UserRoleRepositoryImpl(DatabaseClient db) {
        this.db = db;
    }

    @Override
    public Mono<List<String>> findRolesByUserId(Long userId) {
        return db.sql("select role from user_roles where user_id = :userId")
                .bind("userId", userId)
                .map((row, meta) -> row.get("role", String.class))
                .all()
                .collectList();
    }

    @Override
    public Mono<Void> addRoles(Long userId, Collection<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return Mono.empty();
        }

        return Flux.fromIterable(roles)
                .flatMap(role -> db
                        .sql("insert into user_roles (user_id, role) values (:userId, :role)")
                        .bind("userId", userId)
                        .bind("role", role)
                        .fetch()
                        .rowsUpdated()
                )
                .then();
    }

    @Override
    public Mono<Void> replaceRoles(Long userId, Collection<String> roles) {
        return db.sql("delete from user_roles where user_id = :userId")
                .bind("userId", userId)
                .fetch()
                .rowsUpdated()
                .then(addRoles(userId, roles));
    }

    @Override
    public Mono<Boolean> existsAnyUserWithRole(String role) {
        return db.sql("select 1 from user_roles where role = :role limit 1")
                .bind("role", role)
                .map((row, meta) -> 1)
                .first()
                .map(x -> true)
                .defaultIfEmpty(false);
    }
}
