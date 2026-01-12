package rs.master.o2c.auth.impl;

import org.springframework.stereotype.Service;
import rs.master.o2c.auth.config.TotpProperties;
import rs.master.o2c.auth.service.LoginChallengeService;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class InMemoryLoginChallengeService implements LoginChallengeService {

    private final Map<String, LoginChallenge> challenges = new ConcurrentHashMap<>();
    private final Duration ttl;

    public InMemoryLoginChallengeService(TotpProperties props) {
        this.ttl = props.loginChallengeTtl();
    }

    @Override
    public LoginChallenge createChallenge(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username is required");
        }

        Instant now = Instant.now();
        cleanupExpired(now);

        String id = UUID.randomUUID().toString();
        LoginChallenge ch = new LoginChallenge(id, username.trim(), now, now.plus(ttl));
        challenges.put(id, ch);
        return ch;
    }

    @Override
    public LoginChallenge consume(String challengeId) {
        if (challengeId == null || challengeId.isBlank()) {
            return null;
        }

        Instant now = Instant.now();
        cleanupExpired(now);

        LoginChallenge ch = challenges.remove(challengeId);
        if (ch == null) {
            return null;
        }
        if (ch.expiresAt() != null && now.isAfter(ch.expiresAt())) {
            return null;
        }
        return ch;
    }

    private void cleanupExpired(Instant now) {
        challenges.entrySet().removeIf(e -> {
            LoginChallenge ch = e.getValue();
            return ch != null && ch.expiresAt() != null && now.isAfter(ch.expiresAt());
        });
    }
}
