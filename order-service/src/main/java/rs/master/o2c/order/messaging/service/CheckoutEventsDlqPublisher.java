package rs.master.o2c.order.messaging.service;

import reactor.core.publisher.Mono;
import reactor.kafka.receiver.ReceiverRecord;

public interface CheckoutEventsDlqPublisher {

    Mono<Void> publish(ReceiverRecord<String, String> record, Throwable cause);
}
