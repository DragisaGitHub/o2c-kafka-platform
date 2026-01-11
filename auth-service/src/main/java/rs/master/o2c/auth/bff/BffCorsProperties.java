package rs.master.o2c.auth.bff;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "bff.cors")
public record BffCorsProperties(
        List<String> allowedOrigins
) {
    public BffCorsProperties {
        if (allowedOrigins == null) {
            allowedOrigins = List.of();
        }
    }
}
