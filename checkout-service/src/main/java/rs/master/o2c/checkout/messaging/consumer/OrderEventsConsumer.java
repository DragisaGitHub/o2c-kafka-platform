package rs.master.o2c.checkout.messaging.consumer;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.reactive.ReactiveKafkaConsumerTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import rs.master.o2c.checkout.messaging.handler.OrderEventsHandler;

@Component
@RequiredArgsConstructor
public class OrderEventsConsumer {

    private final ReactiveKafkaConsumerTemplate<String, String> consumer;
    private final OrderEventsHandler handler;

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        consumer.receive()
                .concatMap(record ->
                        handler.handle(record.value())
                                .doOnError(e -> System.out.println("Handler failed: " + e.getMessage()))
                                .then(Mono.fromRunnable(() -> record.receiverOffset().acknowledge()))
                )
                .doOnError(ex -> System.out.println("Checkout consumer error: " + ex.getMessage()))
                .retry()
                .subscribe();
    }
}