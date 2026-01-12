package rs.master.o2c.auth.service;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import rs.master.o2c.auth.persistence.entity.UserEntity;
import rs.master.o2c.auth.persistence.entity.UserMfaEntity;
import rs.master.o2c.auth.persistence.repository.UserMfaRepository;
import rs.master.o2c.auth.persistence.repository.UserRepository;
import rs.master.o2c.auth.impl.TotpUserMfaServiceImpl;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TotpUserMfaServiceTest {

    @Test
    void loadTotpMfaReadsFromUserMfaTable() {
        UserRepository users = mock(UserRepository.class);
        UserMfaRepository userMfa = mock(UserMfaRepository.class);

        TotpUserMfaService svc = new TotpUserMfaServiceImpl(users, userMfa);

        UserEntity u = new UserEntity(10L, "pera", "hash", true, Instant.now(), null);
        u.markNotNew();

        when(users.findByUsername(eq("pera"))).thenReturn(Mono.just(u));

        UserMfaEntity mfa = new UserMfaEntity(10L, true, new byte[]{1, 2, 3}, Instant.now(), Instant.now(), null);
        when(userMfa.findById(eq(10L))).thenReturn(Mono.just(mfa));

        StepVerifier.create(svc.loadTotpMfa("pera"))
                .expectNextMatches(r -> r.enabled() && r.secretEnc() != null && r.secretEnc().length == 3)
                .verifyComplete();
    }

    @Test
    void loadTotpMfaMissingRowTreatsAsDisabled() {
        UserRepository users = mock(UserRepository.class);
        UserMfaRepository userMfa = mock(UserMfaRepository.class);

        TotpUserMfaService svc = new TotpUserMfaServiceImpl(users, userMfa);

        UserEntity u = new UserEntity(11L, "nina", "hash", true, Instant.now(), null);
        u.markNotNew();

        when(users.findByUsername(eq("nina"))).thenReturn(Mono.just(u));
        when(userMfa.findById(eq(11L))).thenReturn(Mono.empty());

        StepVerifier.create(svc.loadTotpMfa("nina"))
                .expectNextMatches(r -> !r.enabled() && r.secretEnc() == null)
                .verifyComplete();
    }
}
