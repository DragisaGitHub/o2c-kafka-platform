package rs.master.o2c.checkout.messaging.impl;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.reactive.ReactiveKafkaConsumerTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import rs.master.o2c.checkout.messaging.handler.OrderEventsHandler;
import rs.master.o2c.checkout.messaging.service.OrderEventsConsumerRunner;
import rs.master.o2c.checkout.messaging.service.OrderEventsDlqPublisher;
import rs.master.o2c.events.CorrelationHeaders;
import rs.master.o2c.infra.correlation.CorrelationIdSupport;
import rs.master.o2c.infra.kafka.KafkaRetryPolicies;

@Service
@RequiredArgsConstructor
public class OrderEventsConsumerRunnerImpl implements OrderEventsConsumerRunner {

    private static final Logger log = LoggerFactory.getLogger(OrderEventsConsumerRunnerImpl.class);

    private final ReactiveKafkaConsumerTemplate<String, String> consumer;
    private final OrderEventsHandler handler;
    private final OrderEventsDlqPublisher dlqPublisher;

    @Override
    public void start() {
        consumer.receive()
                .concatMap(record ->
                        handler.handle(record.value())
                                .retryWhen(KafkaRetryPolicies.processingRetry())
                                .then(Mono.fromRunnable(record.receiverOffset()::acknowledge))
                                .onErrorResume(e -> {
                                    log.error(
                                            "order-events handler failed after retries correlationId={} partition={} offset={}",
                                            CorrelationIdSupport.headerValueOrNA(record.headers().lastHeader(CorrelationHeaders.X_CORRELATION_ID)),
                                            record.partition(),
                                            record.offset(),
                                            e
                                    );

                                    return dlqPublisher.publish(record, e)
                                            .retryWhen(KafkaRetryPolicies.dlqRetry())
                                            .then(Mono.fromRunnable(record.receiverOffset()::acknowledge));
                                })
                )
                .doOnError(ex -> log.error("order-events consumer stream error correlationId=n/a", ex))
                .retryWhen(KafkaRetryPolicies.streamRetry())
                .subscribe();
    }
}
