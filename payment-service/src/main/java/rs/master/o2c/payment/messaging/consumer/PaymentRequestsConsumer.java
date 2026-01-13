package rs.master.o2c.payment.messaging.consumer;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import rs.master.o2c.payment.messaging.service.PaymentRequestsConsumerRunner;

@Component
@RequiredArgsConstructor
public class PaymentRequestsConsumer {

    private final PaymentRequestsConsumerRunner runner;

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        runner.start();
    }
}