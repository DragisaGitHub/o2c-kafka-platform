package rs.master.o2c.payment.messaging.impl;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.reactive.ReactiveKafkaConsumerTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import rs.master.o2c.events.CorrelationHeaders;
import rs.master.o2c.infra.correlation.CorrelationIdSupport;
import rs.master.o2c.infra.kafka.KafkaRetryPolicies;
import rs.master.o2c.payment.messaging.handler.PaymentRequestsHandler;
import rs.master.o2c.payment.messaging.service.PaymentRequestsConsumerRunner;
import rs.master.o2c.payment.messaging.service.PaymentRequestsDlqPublisher;

@Service
@RequiredArgsConstructor
public class PaymentRequestsConsumerRunnerImpl implements PaymentRequestsConsumerRunner {

    private static final Logger log = LoggerFactory.getLogger(PaymentRequestsConsumerRunnerImpl.class);

    private final ReactiveKafkaConsumerTemplate<String, String> consumer;
    private final PaymentRequestsHandler handler;
    private final PaymentRequestsDlqPublisher dlqPublisher;

    @Override
    public void start() {
        consumer.receive()
                .concatMap(record ->
                        handler.handle(record.value())
                                .retryWhen(KafkaRetryPolicies.processingRetry())
                                .then(Mono.fromRunnable(record.receiverOffset()::acknowledge))
                                .onErrorResume(e -> {
                                    log.error(
                                            "payment-requests handler failed after retries correlationId={} partition={} offset={}",
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
                .doOnError(ex -> log.error("payment-requests consumer stream error correlationId=n/a", ex))
                .retryWhen(KafkaRetryPolicies.streamRetry())
                .subscribe();
    }
}
