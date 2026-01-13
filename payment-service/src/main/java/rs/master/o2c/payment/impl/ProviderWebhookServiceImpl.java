package rs.master.o2c.payment.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import rs.master.o2c.events.EventEnvelope;
import rs.master.o2c.events.EventTypes;
import rs.master.o2c.events.ProducerNames;
import rs.master.o2c.events.payment.PaymentCompleted;
import rs.master.o2c.events.payment.PaymentFailed;
import rs.master.o2c.events.payment.PaymentStatus;
import rs.master.o2c.payment.kafka.PaymentEventPublisher;
import rs.master.o2c.payment.persistence.entity.PaymentAttemptEntity;
import rs.master.o2c.payment.persistence.entity.PaymentEntity;
import rs.master.o2c.payment.persistence.repository.PaymentAttemptRepository;
import rs.master.o2c.payment.persistence.repository.PaymentRepository;
import rs.master.o2c.payment.service.ProviderWebhookService;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
public class ProviderWebhookServiceImpl implements ProviderWebhookService {

    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentEventPublisher paymentEventPublisher;
    private final ObjectMapper objectMapper;

    public ProviderWebhookServiceImpl(
            PaymentAttemptRepository paymentAttemptRepository,
            PaymentRepository paymentRepository,
            PaymentEventPublisher paymentEventPublisher,
            ObjectMapper objectMapper
    ) {
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.paymentRepository = paymentRepository;
        this.paymentEventPublisher = paymentEventPublisher;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handleWebhook(
            UUID providerPaymentId,
            String status,
            String failureReason,
            String correlationId
    ) {
        return paymentAttemptRepository
                .findByProviderPaymentId(providerPaymentId.toString())
                .flatMap(attempt -> processKnownAttempt(attempt, providerPaymentId, status, failureReason, correlationId))
                .switchIfEmpty(Mono.fromRunnable(() ->
                        log.warn("provider webhook unknown providerPaymentId={} status={} correlationId={}", providerPaymentId, status, correlationId)
                ))
                .then();
    }

    private Mono<Void> processKnownAttempt(
            PaymentAttemptEntity attempt,
            UUID providerPaymentId,
            String status,
            String failureReason,
            String correlationId
    ) {
        if (PaymentStatus.SUCCEEDED.equals(attempt.status()) || PaymentStatus.FAILED.equals(attempt.status())) {
            log.info(
                    "provider webhook duplicate ignored providerPaymentId={} attemptId={} attemptStatus={} correlationId={} ",
                    providerPaymentId,
                    attempt.id(),
                    attempt.status(),
                    correlationId
            );
            return Mono.empty();
        }

        String safeReason = (failureReason == null || failureReason.isBlank()) ? null : failureReason.trim();

        Mono<Void> updateAttempt = paymentAttemptRepository
                .updateStatusAndReasonById(attempt.id(), status, safeReason)
                .then();

        Mono<Void> updatePaymentAndPublish = paymentRepository
                .findById(attempt.paymentId())
                .flatMap(payment -> updatePaymentIfNeededAndPublish(payment, providerPaymentId.toString(), status, safeReason, correlationId));

        return updateAttempt.then(updatePaymentAndPublish);
    }

    private Mono<Void> updatePaymentIfNeededAndPublish(
            PaymentEntity payment,
            String providerPaymentId,
            String status,
            String failureReason,
            String correlationId
    ) {
        if (PaymentStatus.SUCCEEDED.equals(payment.status()) || PaymentStatus.FAILED.equals(payment.status())) {
            // Idempotency: do not republish events.
            return Mono.empty();
        }

        if (PaymentStatus.SUCCEEDED.equals(status)) {
            payment.markSucceeded(providerPaymentId);
        } else if (PaymentStatus.FAILED.equals(status)) {
            payment.markFailed(failureReason == null ? "UNKNOWN" : failureReason);
        } else {
            return Mono.empty();
        }

        payment.markNotNew();

        return paymentRepository
                .save(payment)
                .then(publishOutcome(payment, correlationId));
    }

    private Mono<Void> publishOutcome(PaymentEntity payment, String correlationId) {
        UUID cid = parseUuidOrNull(correlationId);

        if (PaymentStatus.SUCCEEDED.equals(payment.status())) {
            return publishCompleted(payment, cid);
        }
        if (PaymentStatus.FAILED.equals(payment.status())) {
            return publishFailed(payment, cid);
        }
        return Mono.empty();
    }

    private Mono<Void> publishCompleted(PaymentEntity payment, UUID correlationId) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(
                        new EventEnvelope<>(
                                UUID.randomUUID(),
                                correlationId,
                                null,
                                EventTypes.PAYMENT_COMPLETED,
                                1,
                                Instant.now(),
                                ProducerNames.PAYMENT_SERVICE,
                                payment.orderId(),
                                new PaymentCompleted(
                                        payment.id(),
                                        payment.checkoutId(),
                                        payment.orderId(),
                                        payment.totalAmount(),
                                        payment.currency()
                                )
                        )
                ))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(json -> paymentEventPublisher.publishPaymentEvent(payment.orderId(), json));
    }

    private Mono<Void> publishFailed(PaymentEntity payment, UUID correlationId) {
        String reason = (payment.failureReason() == null || payment.failureReason().isBlank()) ? "UNKNOWN" : payment.failureReason();

        return Mono.fromCallable(() -> objectMapper.writeValueAsString(
                        new EventEnvelope<>(
                                UUID.randomUUID(),
                                correlationId,
                                null,
                                EventTypes.PAYMENT_FAILED,
                                1,
                                Instant.now(),
                                ProducerNames.PAYMENT_SERVICE,
                                payment.orderId(),
                                new PaymentFailed(
                                        payment.id(),
                                        payment.checkoutId(),
                                        payment.orderId(),
                                        reason
                                )
                        )
                ))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(json -> paymentEventPublisher.publishPaymentEvent(payment.orderId(), json));
    }

    private static UUID parseUuidOrNull(String v) {
        if (v == null || v.isBlank()) return null;
        try {
            return UUID.fromString(v.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
