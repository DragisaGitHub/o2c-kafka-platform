package rs.master.o2c.auth.service;

import reactor.core.publisher.Mono;

import java.time.Instant;

public interface TotpUserMfaService {

    record UserTotpMfa(boolean enabled, byte[] secretEnc) {}

    Mono<Boolean> userExists(String username);

    Mono<UserTotpMfa> loadTotpMfa(String username);

    Mono<Void> enableTotp(String username, byte[] secretEnc, Instant enrolledAt);
}
