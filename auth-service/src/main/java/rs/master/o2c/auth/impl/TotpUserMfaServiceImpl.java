package rs.master.o2c.auth.impl;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import rs.master.o2c.auth.persistence.repository.UserMfaRepository;
import rs.master.o2c.auth.persistence.repository.UserRepository;
import rs.master.o2c.auth.service.TotpUserMfaService;

import java.time.Instant;

@Service
public class TotpUserMfaServiceImpl implements TotpUserMfaService {

    private final UserRepository users;
    private final UserMfaRepository userMfa;

    public TotpUserMfaServiceImpl(UserRepository users, UserMfaRepository userMfa) {
        this.users = users;
        this.userMfa = userMfa;
    }

    @Override
    public Mono<Boolean> userExists(String username) {
        if (username == null || username.isBlank()) {
            return Mono.just(false);
        }
        return users.findByUsername(username.trim())
                .map(u -> true)
                .defaultIfEmpty(false);
    }

    @Override
    public Mono<UserTotpMfa> loadTotpMfa(String username) {
        if (username == null || username.isBlank()) {
            return Mono.empty();
        }

        String normalized = username.trim();
        return users.findByUsername(normalized)
                .flatMap(u -> userMfa.findById(u.id())
                        .map(mfa -> new UserTotpMfa(mfa.totpEnabled(), mfa.totpSecretEnc()))
                        // Missing user_mfa row is treated as disabled.
                        .defaultIfEmpty(new UserTotpMfa(false, null))
                );
    }

    @Override
    public Mono<Void> enableTotp(String username, byte[] secretEnc, Instant enrolledAt) {
        if (username == null || username.isBlank()) {
            return Mono.error(new IllegalArgumentException("username is required"));
        }
        if (secretEnc == null || secretEnc.length == 0) {
            return Mono.error(new IllegalArgumentException("secretEnc is required"));
        }

        String normalized = username.trim();

        return users.findByUsername(normalized)
                .switchIfEmpty(Mono.error(new IllegalStateException("NOT_FOUND")))
                .flatMap(u -> userMfa.upsertTotp(u.id(), true, secretEnc, enrolledAt))
                .then();
    }
}
