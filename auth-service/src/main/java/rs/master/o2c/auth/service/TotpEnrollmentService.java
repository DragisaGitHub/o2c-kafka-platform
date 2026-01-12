package rs.master.o2c.auth.service;

import java.time.Instant;

public interface TotpEnrollmentService {

    record SetupSession(
            String setupId,
            String username,
            String issuer,
            byte[] secret,
            Instant createdAt,
            Instant expiresAt
    ) {}

    SetupSession createSetup(String username);

    SetupSession getValidSetup(String setupId);

    SetupSession consumeSetup(String setupId);
}
