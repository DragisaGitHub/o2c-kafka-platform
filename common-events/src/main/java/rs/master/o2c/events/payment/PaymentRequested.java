package rs.master.o2c.events.payment;

import java.math.BigDecimal;

public record PaymentRequested(
        String checkoutId,
        String orderId,
        String customerId,
        BigDecimal amount,
        String currency
) {}