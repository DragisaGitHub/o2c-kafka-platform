package rs.master.o2c.auth.service.model;

import java.time.Instant;

public record PinChallenge(
        String challengeId,
        String username,
        String pin,
        Instant createdAt
) {}