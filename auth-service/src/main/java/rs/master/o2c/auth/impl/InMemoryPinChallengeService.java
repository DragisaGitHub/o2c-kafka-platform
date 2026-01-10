package rs.master.o2c.auth.impl;

import org.springframework.stereotype.Service;
import rs.master.o2c.auth.service.PinChallengeService;
import rs.master.o2c.auth.service.model.PinChallenge;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class InMemoryPinChallengeService implements PinChallengeService {

    private static final int PIN_LENGTH = 5;
    private static final int MAX_PIN_VALUE = 100_000;

    private final SecureRandom random = new SecureRandom();

    private final Map<String, PinChallenge> challenges = new ConcurrentHashMap<>();

    @Override
    public PinChallenge createChallenge(String username) {
        String challengeId = UUID.randomUUID().toString();
        String pin = generatePin();

        PinChallenge challenge = new PinChallenge(
                challengeId,
                username,
                pin,
                Instant.now()
        );

        challenges.put(challengeId, challenge);
        return challenge;
    }
    @Override
    public PinChallenge consume(String challengeId) {
        return challenges.remove(challengeId);
    }


    private String generatePin() {
        int pin = random.nextInt(MAX_PIN_VALUE);
        return String.format("%0" + PIN_LENGTH + "d", pin);
    }
}