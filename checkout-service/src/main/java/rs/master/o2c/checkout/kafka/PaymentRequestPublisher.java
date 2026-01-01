package rs.master.o2c.checkout.kafka;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import rs.master.o2c.events.CorrelationHeaders;
import rs.master.o2c.events.TopicNames;

import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class PaymentRequestPublisher {

    private final ReactiveKafkaProducerTemplate<String, String> producer;

    public Mono<Void> publishPaymentRequested(String key, String jsonPayload, String correlationId) {
        ProducerRecord<String, String> record = new ProducerRecord<>(TopicNames.PAYMENT_REQUESTS_V1, key, jsonPayload);

        if (correlationId != null && !correlationId.isBlank()) {
            record.headers().add(
                    CorrelationHeaders.X_CORRELATION_ID,
                    correlationId.trim().getBytes(StandardCharsets.UTF_8)
            );
        }

        return producer.send(record).then();
    }
}
