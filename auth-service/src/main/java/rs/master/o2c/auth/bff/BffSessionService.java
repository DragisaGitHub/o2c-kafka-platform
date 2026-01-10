package rs.master.o2c.auth.bff;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BffSessionService {

    public static final String COOKIE_NAME = "O2C_BFF_SESSION";

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public record Session(String username, String accessToken, Instant expiresAt) {
        public boolean isExpired(Instant now) {
            return expiresAt != null && now.isAfter(expiresAt);
        }
    }

    public String createSession(String username, String accessToken, Duration ttl) {
        String sessionId = UUID.randomUUID().toString();
        Instant expiresAt = ttl == null ? null : Instant.now().plus(ttl);
        sessions.put(sessionId, new Session(username, accessToken, expiresAt));
        return sessionId;
    }

    public Session getValidSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }

        Session session = sessions.get(sessionId);
        if (session == null) {
            return null;
        }

        Instant now = Instant.now();
        if (session.isExpired(now)) {
            sessions.remove(sessionId);
            return null;
        }

        return session;
    }

    public void invalidate(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        sessions.remove(sessionId);
    }
}
