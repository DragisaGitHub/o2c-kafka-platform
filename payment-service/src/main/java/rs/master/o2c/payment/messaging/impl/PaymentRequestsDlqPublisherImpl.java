package rs.master.o2c.payment.messaging.impl;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.ReceiverRecord;
import rs.master.o2c.events.TopicNames;
import rs.master.o2c.infra.kafka.DlqRecordSupport;
import rs.master.o2c.payment.messaging.service.PaymentRequestsDlqPublisher;

@Service
public class PaymentRequestsDlqPublisherImpl implements PaymentRequestsDlqPublisher {

    private static final Logger log = LoggerFactory.getLogger(PaymentRequestsDlqPublisherImpl.class);

    private final ReactiveKafkaProducerTemplate<String, String> producer;

    public PaymentRequestsDlqPublisherImpl(ReactiveKafkaProducerTemplate<String, String> producer) {
        this.producer = producer;
    }

    @Override
    public Mono<Void> publish(ReceiverRecord<String, String> record, Throwable cause) {
        return Mono.defer(() -> {
            ProducerRecord<String, String> out = new ProducerRecord<>(
                    TopicNames.PAYMENT_REQUESTS_DLQ_V1,
                    record.key(),
                    record.value()
            );

            DlqRecordSupport.enrich(out, record, cause);

            return producer.send(out)
                    .doOnSuccess(r -> log.warn(
                            "Published to payment-requests DLQ topic={} partition={} offset={} key={} errorClass={}",
                            TopicNames.PAYMENT_REQUESTS_DLQ_V1,
                            record.partition(),
                            record.offset(),
                            record.key(),
                            cause.getClass().getName()
                    ))
                    .then();
        });
    }
}
