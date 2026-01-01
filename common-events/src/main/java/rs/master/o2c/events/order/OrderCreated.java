package rs.master.o2c.events.order;

import rs.master.o2c.events.Money;

public record OrderCreated(
        String orderId,
        String customerId,
        Money total,
        String status
) {}