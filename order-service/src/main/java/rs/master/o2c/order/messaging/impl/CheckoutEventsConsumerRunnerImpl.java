package rs.master.o2c.order.messaging.impl;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.reactive.ReactiveKafkaConsumerTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import rs.master.o2c.events.CorrelationHeaders;
import rs.master.o2c.infra.correlation.CorrelationIdSupport;
import rs.master.o2c.infra.kafka.KafkaRetryPolicies;
import rs.master.o2c.order.messaging.handler.CheckoutEventsHandler;
import rs.master.o2c.order.messaging.service.CheckoutEventsConsumerRunner;
import rs.master.o2c.order.messaging.service.CheckoutEventsDlqPublisher;

@Service
@RequiredArgsConstructor
public class CheckoutEventsConsumerRunnerImpl implements CheckoutEventsConsumerRunner {

    private static final Logger log = LoggerFactory.getLogger(CheckoutEventsConsumerRunnerImpl.class);

    private final ReactiveKafkaConsumerTemplate<String, String> checkoutEventsConsumerTemplate;
    private final CheckoutEventsHandler handler;
    private final CheckoutEventsDlqPublisher dlqPublisher;

    @Override
    public void start() {
        checkoutEventsConsumerTemplate
                .receive()
                .concatMap(record ->
                        handler.handle(record.value())
                                .retryWhen(KafkaRetryPolicies.processingRetry())
                                .then(Mono.fromRunnable(record.receiverOffset()::acknowledge))
                                .onErrorResume(e -> {
                                    log.error(
                                            "checkout-events handler failed after retries correlationId={} partition={} offset={}",
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
                .doOnError(e -> log.error("checkout-events consumer stream error correlationId=n/a", e))
                .retryWhen(KafkaRetryPolicies.streamRetry())
                .subscribe();
    }
}
