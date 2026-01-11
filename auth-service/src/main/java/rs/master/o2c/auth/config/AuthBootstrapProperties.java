package rs.master.o2c.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.bootstrap")
public record AuthBootstrapProperties(
        String username,
        String password
) {
}
