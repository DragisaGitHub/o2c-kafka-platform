package rs.master.o2c.provider.scheduling;

import java.util.UUID;

public record ProviderCallbackTask(
        String webhookUrl,
        String correlationId,
        UUID providerPaymentId,
        String status,
        String failureReason
) {
}
