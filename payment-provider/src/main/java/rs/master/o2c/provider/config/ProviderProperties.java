package rs.master.o2c.provider.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "provider")
public record ProviderProperties(
        long callbackDelayMs,
        String webhookUrl
) {
}
