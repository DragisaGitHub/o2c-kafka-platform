package rs.master.o2c.order.messaging.consumer;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import rs.master.o2c.order.messaging.service.CheckoutEventsConsumerRunner;

@Component
@RequiredArgsConstructor
public class CheckoutEventsConsumer {

    private final CheckoutEventsConsumerRunner runner;

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        runner.start();
    }
}