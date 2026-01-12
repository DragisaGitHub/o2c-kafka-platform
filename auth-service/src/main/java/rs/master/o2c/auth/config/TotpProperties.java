package rs.master.o2c.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "mfa.totp")
public record TotpProperties(
        String issuer,
        long setupTtlSeconds,
        long loginChallengeTtlSeconds,
        String encryptionKey
) {
    public TotpProperties {
        if (issuer == null || issuer.isBlank()) {
            issuer = "O2C";
        }
        if (setupTtlSeconds <= 0) {
            setupTtlSeconds = 600;
        }
        if (loginChallengeTtlSeconds <= 0) {
            loginChallengeTtlSeconds = 180;
        }
        if (encryptionKey == null || encryptionKey.isBlank()) {
            throw new IllegalArgumentException("mfa.totp.encryption-key must be set");
        }
    }

    public Duration setupTtl() {
        return Duration.ofSeconds(setupTtlSeconds);
    }

    public Duration loginChallengeTtl() {
        return Duration.ofSeconds(loginChallengeTtlSeconds);
    }
}
