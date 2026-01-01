package rs.master.o2c.payment.config;

import java.util.List;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.reactive.ReactiveKafkaConsumerTemplate;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;

import reactor.kafka.receiver.ReceiverOptions;
import reactor.kafka.sender.SenderOptions;
import rs.master.o2c.events.TopicNames;

@Configuration
public class KafkaReactiveConfig {

    @Bean
    public ReactiveKafkaProducerTemplate<String, String> reactiveKafkaProducerTemplate(KafkaProperties properties) {
        Map<String, Object> producerProps = properties.buildProducerProperties();
        SenderOptions<String, String> senderOptions = SenderOptions.create(producerProps);
        return new ReactiveKafkaProducerTemplate<>(senderOptions);
    }

    @Bean
    public ReactiveKafkaConsumerTemplate<String, String> reactiveKafkaConsumerTemplate(KafkaProperties properties) {
        Map<String, Object> consumerProps = properties.buildConsumerProperties();
        consumerProps.putIfAbsent(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        ReceiverOptions<String, String> receiverOptions =
                ReceiverOptions.<String, String>create(consumerProps)
                        .subscription(List.of(TopicNames.PAYMENT_REQUESTS_V1));

        return new ReactiveKafkaConsumerTemplate<>(receiverOptions);
    }
}