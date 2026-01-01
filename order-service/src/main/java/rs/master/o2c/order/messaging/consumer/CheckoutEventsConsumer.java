package rs.master.o2c.order.messaging.consumer;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.reactive.ReactiveKafkaConsumerTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import rs.master.o2c.order.messaging.handler.CheckoutEventsHandler;

@Component
@RequiredArgsConstructor
public class CheckoutEventsConsumer {

    private final ReactiveKafkaConsumerTemplate<String, String> checkoutEventsConsumerTemplate;
    private final CheckoutEventsHandler handler;

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        checkoutEventsConsumerTemplate
                .receive()
                .concatMap(record ->
                        handler.handle(record.value())
                                .doOnError(e -> System.out.println("CheckoutEventsHandler failed: " + e.getMessage()))
                                .then(Mono.fromRunnable(record.receiverOffset()::acknowledge))
                )
                .doOnError(e -> System.out.println("CheckoutEventsConsumer error: " + e.getMessage()))
                .retry()
                .subscribe();
    }
}