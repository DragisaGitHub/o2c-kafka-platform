package rs.master.o2c.checkout.messaging.consumer;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.reactive.ReactiveKafkaConsumerTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import rs.master.o2c.events.CorrelationHeaders;
import rs.master.o2c.checkout.messaging.handler.OrderEventsHandler;

import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class OrderEventsConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventsConsumer.class);

    private final ReactiveKafkaConsumerTemplate<String, String> consumer;
    private final OrderEventsHandler handler;

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        consumer.receive()
                .concatMap(record ->
                        handler.handle(record.value())
                                .doOnError(e -> log.error(
                                        "order-events handler failed correlationId={}",
                                        correlationIdOrNA(record.headers().lastHeader(CorrelationHeaders.X_CORRELATION_ID)),
                                        e
                                ))
                                .then(Mono.fromRunnable(() -> record.receiverOffset().acknowledge()))
                )
                .doOnError(ex -> log.error("order-events consumer error correlationId=n/a", ex))
                .retry()
                .subscribe();
    }

    private static String correlationIdOrNA(Header header) {
        if (header == null || header.value() == null || header.value().length == 0) {
            return "n/a";
        }

        String value = new String(header.value(), StandardCharsets.UTF_8).trim();
        return value.isEmpty() ? "n/a" : value;
    }
}