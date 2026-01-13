package rs.master.o2c.payment.messaging.service;

import reactor.kafka.receiver.ReceiverRecord;
import reactor.core.publisher.Mono;

public interface PaymentRequestsDlqPublisher {

    Mono<Void> publish(ReceiverRecord<String, String> record, Throwable cause);
}
