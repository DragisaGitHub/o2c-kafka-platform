package rs.master.o2c.auth.impl;

import org.springframework.stereotype.Service;
import rs.master.o2c.auth.config.AuthMfaProperties;
import rs.master.o2c.auth.service.PinChallengeService;
import rs.master.o2c.auth.service.model.PinChallenge;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class InMemoryPinChallengeService implements PinChallengeService {

    private static final int PIN_LENGTH = 5;
    private static final int MAX_PIN_VALUE = 100_000;

    private final SecureRandom random = new SecureRandom();

    private final Duration ttl;

    private final Map<String, PinChallenge> challenges = new ConcurrentHashMap<>();

    public InMemoryPinChallengeService(AuthMfaProperties props) {
        this.ttl = props.challengeTtl();
    }

    @Override
    public PinChallenge createChallenge(String username) {
        cleanupExpired(Instant.now());
        String challengeId = UUID.randomUUID().toString();
        String pin = generatePin();

        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);

        PinChallenge challenge = new PinChallenge(
                challengeId,
                username,
                pin,
            now,
            expiresAt
        );

        challenges.put(challengeId, challenge);
        return challenge;
    }
    @Override
    public PinChallenge consume(String challengeId) {
        if (challengeId == null || challengeId.isBlank()) {
            return null;
        }

        Instant now = Instant.now();
        cleanupExpired(now);

        PinChallenge ch = challenges.remove(challengeId);
        if (ch == null) {
            return null;
        }
        if (isExpired(ch, now)) {
            return null;
        }
        return ch;
    }

    private void cleanupExpired(Instant now) {
        challenges.entrySet().removeIf(e -> isExpired(e.getValue(), now));
    }

    private static boolean isExpired(PinChallenge ch, Instant now) {
        return ch.expiresAt() != null && now.isAfter(ch.expiresAt());
    }


    private String generatePin() {
        int pin = random.nextInt(MAX_PIN_VALUE);
        return String.format("%0" + PIN_LENGTH + "d", pin);
    }
}