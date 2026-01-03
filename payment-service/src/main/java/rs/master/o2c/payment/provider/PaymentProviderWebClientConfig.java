package rs.master.o2c.payment.provider;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class PaymentProviderWebClientConfig {

    @Bean
    WebClient providerWebClient(WebClient.Builder builder, PaymentProviderProperties properties) {
        return builder
                .baseUrl(properties.baseUrl())
                .build();
    }
}
