package rs.master.o2c.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.jwt")
public record AuthJwtProperties(
        String secret,
        long expiresInMinutes
) {
    public AuthJwtProperties {
        if (expiresInMinutes <= 0) {
            expiresInMinutes = 60;
        }
    }
}