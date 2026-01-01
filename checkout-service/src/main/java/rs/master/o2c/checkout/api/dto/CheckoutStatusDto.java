package rs.master.o2c.checkout.api.dto;

public record CheckoutStatusDto(
        String orderId,
        String status
) {}
