package rs.master.o2c.payment.service;

import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ProviderWebhookService {

    Mono<Void> handleWebhook(
            UUID providerPaymentId,
            String status,
            String failureReason,
            String correlationId
    );
}
