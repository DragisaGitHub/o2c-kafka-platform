package rs.master.o2c.order.messaging.mapper;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;
import rs.master.o2c.events.*;
import rs.master.o2c.events.order.OrderCreated;
import rs.master.o2c.order.persistence.entity.OrderEntity;

@Component
public class OrderEventMapper {

    public EventEnvelope<OrderCreated> toOrderCreatedEnvelope(OrderEntity order) {
        UUID messageId = UUID.randomUUID();

        return new EventEnvelope<>(
                messageId,
                messageId,
                null,
                EventTypes.ORDER_CREATED,
                1,
                Instant.now(),
                ProducerNames.ORDER_SERVICE,
                order.id(),
                new OrderCreated(
                        order.id(),
                        order.customerId(),
                        new Money(order.currency(), order.totalAmount()),
                        order.status()
                )
        );
    }
}