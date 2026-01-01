package rs.master.o2c.events.checkout;

public record CheckoutFailed(
        String checkoutId,
        String orderId,
        String reason
) {}