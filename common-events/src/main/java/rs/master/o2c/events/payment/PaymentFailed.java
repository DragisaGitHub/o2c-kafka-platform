package rs.master.o2c.events.payment;

public record PaymentFailed(
        String paymentId,
        String checkoutId,
        String orderId,
        String reason
) {}