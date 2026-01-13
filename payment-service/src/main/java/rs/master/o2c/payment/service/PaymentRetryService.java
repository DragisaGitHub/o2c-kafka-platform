package rs.master.o2c.payment.service;

import reactor.core.publisher.Mono;
import rs.master.o2c.payment.api.dto.RetryPaymentRequest;

import java.util.UUID;

public interface PaymentRetryService {

    Mono<RetryOutcome> retry(RetryPaymentRequest request, String correlationId);

    record RetryOutcome(boolean alreadyAccepted, UUID retryRequestId) {}
}
