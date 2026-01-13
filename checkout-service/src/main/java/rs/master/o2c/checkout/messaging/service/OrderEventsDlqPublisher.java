package rs.master.o2c.checkout.messaging.service;

import reactor.core.publisher.Mono;
import reactor.kafka.receiver.ReceiverRecord;

public interface OrderEventsDlqPublisher {

    Mono<Void> publish(ReceiverRecord<String, String> record, Throwable cause);
}
