package rs.master.o2c.events.payment;

import java.math.BigDecimal;

public record PaymentCompleted(
        String paymentId,
        String checkoutId,
        String orderId,
        BigDecimal amount,
        String currency
) {
}