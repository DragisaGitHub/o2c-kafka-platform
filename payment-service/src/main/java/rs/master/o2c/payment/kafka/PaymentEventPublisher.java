package rs.master.o2c.payment.kafka;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import rs.master.o2c.events.TopicNames;

@Component
@RequiredArgsConstructor
public class PaymentEventPublisher {

    private final ReactiveKafkaProducerTemplate<String, String> producer;

    public Mono<Void> publishPaymentEvent(String key, String jsonPayload) {
        return producer
                .send(TopicNames.PAYMENT_EVENTS_V1, key, jsonPayload)
                .then();
    }
}