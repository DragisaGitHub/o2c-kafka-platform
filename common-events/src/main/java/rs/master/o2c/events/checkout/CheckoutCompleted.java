package rs.master.o2c.events.checkout;

public record CheckoutCompleted(
        String checkoutId,
        String orderId,
        String customerId
) {}