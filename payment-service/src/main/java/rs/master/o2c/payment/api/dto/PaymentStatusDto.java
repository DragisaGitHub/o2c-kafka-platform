package rs.master.o2c.payment.api.dto;

public record PaymentStatusDto(
        String orderId,
        String status,
        String failureReason
) {}
