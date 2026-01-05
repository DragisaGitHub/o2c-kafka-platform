package rs.master.o2c.payment.provider;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payment.provider")
public record PaymentProviderProperties(
        boolean enabled,
        String baseUrl
) {
}
