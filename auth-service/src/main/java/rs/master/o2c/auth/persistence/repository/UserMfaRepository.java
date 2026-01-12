package rs.master.o2c.auth.persistence.repository;

import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;
import rs.master.o2c.auth.persistence.entity.UserMfaEntity;

import java.time.Instant;

public interface UserMfaRepository extends ReactiveCrudRepository<UserMfaEntity, Long> {

    @Modifying
    @Query("""
            insert into user_mfa (user_id, totp_enabled, totp_secret_enc, enrolled_at)
            values (:userId, :totpEnabled, :totpSecretEnc, :enrolledAt)
            on duplicate key update
                totp_enabled = values(totp_enabled),
                totp_secret_enc = values(totp_secret_enc),
                enrolled_at = values(enrolled_at)
            """)
    Mono<Integer> upsertTotp(Long userId, boolean totpEnabled, byte[] totpSecretEnc, Instant enrolledAt);
}
