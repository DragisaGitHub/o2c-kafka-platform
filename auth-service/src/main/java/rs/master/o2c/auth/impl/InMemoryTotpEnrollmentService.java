package rs.master.o2c.auth.impl;

import org.springframework.stereotype.Service;
import rs.master.o2c.auth.config.TotpProperties;
import rs.master.o2c.auth.service.TotpEnrollmentService;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class InMemoryTotpEnrollmentService implements TotpEnrollmentService {

    private final SecureRandom random = new SecureRandom();
    private final Map<String, SetupSession> sessions = new ConcurrentHashMap<>();
    private final Duration ttl;
    private final String issuer;

    public InMemoryTotpEnrollmentService(TotpProperties props) {
        this.ttl = props.setupTtl();
        this.issuer = props.issuer();
    }

    @Override
    public SetupSession createSetup(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username is required");
        }

        Instant now = Instant.now();
        cleanupExpired(now);

        String id = UUID.randomUUID().toString();
        byte[] secret = new byte[20]; // 160-bit
        random.nextBytes(secret);

        SetupSession s = new SetupSession(id, username.trim(), issuer, secret, now, now.plus(ttl));
        sessions.put(id, s);
        return s;
    }

    @Override
    public SetupSession getValidSetup(String setupId) {
        if (setupId == null || setupId.isBlank()) {
            return null;
        }

        Instant now = Instant.now();
        cleanupExpired(now);

        SetupSession s = sessions.get(setupId);
        if (s == null) {
            return null;
        }

        if (s.expiresAt() != null && now.isAfter(s.expiresAt())) {
            sessions.remove(setupId);
            return null;
        }

        return s;
    }

    @Override
    public SetupSession consumeSetup(String setupId) {
        if (setupId == null || setupId.isBlank()) {
            return null;
        }

        Instant now = Instant.now();
        cleanupExpired(now);

        SetupSession s = sessions.remove(setupId);
        if (s == null) {
            return null;
        }

        if (s.expiresAt() != null && now.isAfter(s.expiresAt())) {
            return null;
        }

        return s;
    }

    private void cleanupExpired(Instant now) {
        sessions.entrySet().removeIf(e -> {
            SetupSession s = e.getValue();
            return s != null && s.expiresAt() != null && now.isAfter(s.expiresAt());
        });
    }
}
