package rs.master.o2c.checkout.messaging.consumer;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import rs.master.o2c.checkout.messaging.service.OrderEventsConsumerRunner;

@Component
@RequiredArgsConstructor
public class OrderEventsConsumer {

    private final OrderEventsConsumerRunner runner;

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        runner.start();
    }
}