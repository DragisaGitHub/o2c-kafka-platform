package rs.master.o2c.order.config;

import java.util.List;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.reactive.ReactiveKafkaConsumerTemplate;

import reactor.kafka.receiver.ReceiverOptions;
import rs.master.o2c.events.TopicNames;

@Configuration
public class KafkaReactiveConsumerConfig {

    @Bean
    public ReactiveKafkaConsumerTemplate<String, String> checkoutEventsConsumerTemplate(
            KafkaProperties properties
    ) {
        Map<String, Object> props = properties.buildConsumerProperties();
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        ReceiverOptions<String, String> receiverOptions =
                ReceiverOptions.<String, String>create(props)
                        .subscription(List.of(TopicNames.CHECKOUT_EVENTS_V1));

        return new ReactiveKafkaConsumerTemplate<>(receiverOptions);
    }
}