package rs.master.o2c.payment.messaging.consumer;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.reactive.ReactiveKafkaConsumerTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import rs.master.o2c.payment.messaging.handler.PaymentRequestsHandler;

@Component
@RequiredArgsConstructor
public class PaymentRequestsConsumer {

    private final ReactiveKafkaConsumerTemplate<String, String> consumer;
    private final PaymentRequestsHandler handler;

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        consumer.receive()
                .concatMap(record ->
                        handler.handle(record.value())
                                .doOnError(e -> System.out.println("Handler failed: " + e.getMessage()))
                                .then(Mono.fromRunnable(() -> record.receiverOffset().acknowledge()))
                )
                .doOnError(ex -> System.out.println("Payment consumer error: " + ex.getMessage()))
                .retry()
                .subscribe();
    }
}