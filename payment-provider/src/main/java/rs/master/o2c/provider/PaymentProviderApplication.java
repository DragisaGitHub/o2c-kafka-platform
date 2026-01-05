package rs.master.o2c.provider;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import rs.master.o2c.provider.config.ProviderProperties;

@SpringBootApplication
@EnableConfigurationProperties(ProviderProperties.class)
public class PaymentProviderApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentProviderApplication.class, args);
    }

}
