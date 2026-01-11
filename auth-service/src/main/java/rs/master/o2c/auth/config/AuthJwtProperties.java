package rs.master.o2c.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;

@ConfigurationProperties(prefix = "auth.jwt")
public record AuthJwtProperties(
        String secret,
        long expiresInMinutes
) {
    public AuthJwtProperties {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("auth.jwt.secret must be set");
        }
        int secretBytes = secret.getBytes(StandardCharsets.UTF_8).length;
        if (secretBytes < 32) {
            throw new IllegalArgumentException("auth.jwt.secret must be at least 32 bytes (256 bits) for HMAC JWT signing");
        }
        if (expiresInMinutes <= 0) {
            expiresInMinutes = 60;
        }
    }
}