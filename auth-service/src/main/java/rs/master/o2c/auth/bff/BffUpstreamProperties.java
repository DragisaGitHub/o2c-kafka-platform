package rs.master.o2c.auth.bff;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bff.upstream")
public record BffUpstreamProperties(
        String order,
        String checkout,
        String payment
) {
}
