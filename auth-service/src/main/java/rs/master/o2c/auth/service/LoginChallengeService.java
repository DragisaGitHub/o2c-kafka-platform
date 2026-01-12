package rs.master.o2c.auth.service;

import java.time.Instant;

public interface LoginChallengeService {

    record LoginChallenge(
            String challengeId,
            String username,
            Instant createdAt,
            Instant expiresAt
    ) {}

    LoginChallenge createChallenge(String username);

    LoginChallenge consume(String challengeId);
}
