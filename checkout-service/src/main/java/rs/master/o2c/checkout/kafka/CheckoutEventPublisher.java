package rs.master.o2c.checkout.kafka;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import rs.master.o2c.events.TopicNames;

@Component
@RequiredArgsConstructor
public class CheckoutEventPublisher {

    private final ReactiveKafkaProducerTemplate<String, String> producer;

    public Mono<Void> publishCheckoutEvent(String key, String jsonPayload) {
        return producer
                .send(TopicNames.CHECKOUT_EVENTS_V1, key, jsonPayload)
                .then();
    }
}