package rs.master.o2c.payment.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record RetryPaymentRequest(
        @NotNull UUID orderId,
        @NotNull UUID retryRequestId
) {
}
