package rs.master.o2c.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "auth.mfa")
public record AuthMfaProperties(
        long challengeTtlSeconds
) {
    public AuthMfaProperties {
        if (challengeTtlSeconds <= 0) {
            challengeTtlSeconds = 120;
        }
    }

    public Duration challengeTtl() {
        return Duration.ofSeconds(challengeTtlSeconds);
    }
}
