package rs.master.o2c.payment.messaging.handler;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import rs.master.o2c.events.EventEnvelope;
import rs.master.o2c.events.EventTypes;
import rs.master.o2c.events.ProducerNames;
import rs.master.o2c.events.payment.PaymentCompleted;
import rs.master.o2c.events.payment.PaymentFailed;
import rs.master.o2c.events.payment.PaymentRequested;
import rs.master.o2c.events.payment.PaymentStatus;
import rs.master.o2c.events.payment.PaymentProvider;
import rs.master.o2c.payment.kafka.PaymentEventPublisher;
import rs.master.o2c.payment.persistence.repository.PaymentAttemptRepository;
import rs.master.o2c.payment.persistence.entity.InboxProcessedEntity;
import rs.master.o2c.payment.persistence.entity.PaymentEntity;
import rs.master.o2c.payment.persistence.repository.InboxProcessedRepository;
import rs.master.o2c.payment.persistence.repository.PaymentRepository;
import rs.master.o2c.payment.provider.PaymentProviderClient;
import rs.master.o2c.payment.provider.PaymentProviderProperties;

@Component
@RequiredArgsConstructor
public class PaymentRequestsHandler {

    private final ObjectMapper objectMapper;
    private final PaymentRepository paymentRepository;
        private final PaymentAttemptRepository paymentAttemptRepository;
    private final InboxProcessedRepository inboxProcessedRepository;
    private final PaymentEventPublisher paymentEventPublisher;
        private final PaymentProviderProperties paymentProviderProperties;
        private final PaymentProviderClient paymentProviderClient;

    public Mono<Void> handle(String payload) {
        return Mono.fromCallable(() ->
                        objectMapper.readValue(payload, new TypeReference<EventEnvelope<PaymentRequested>>() {})
                )
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(envelope -> {
                    String messageId = envelope.messageId().toString();

                    return inboxProcessedRepository
                            .save(new InboxProcessedEntity(messageId, Instant.now()))
                            .then(process(envelope))
                            .onErrorResume(DuplicateKeyException.class, e -> Mono.empty());
                });
    }

    private Mono<Void> process(EventEnvelope<PaymentRequested> envelope) {
        if (!EventTypes.PAYMENT_REQUESTED.equals(envelope.eventType())) {
            return Mono.empty();
        }

        PaymentRequested ev = envelope.payload();

        return findOrCreatePayment(ev)
                .flatMap(saved ->
                        createAttempt(saved.id(), ev)
                                .flatMap(attemptNo -> attemptPayment(saved, envelope, ev, attemptNo)
                                        .onErrorResume(ex -> markFailed(saved, envelope, ev, attemptNo, ex.getMessage()))
                                )
                )
                .onErrorResume(DataIntegrityViolationException.class, e -> Mono.empty())
                .then();
    }

    private Mono<PaymentEntity> findOrCreatePayment(PaymentRequested ev) {
        return paymentRepository
                .findByCheckoutId(ev.checkoutId())
                .map(existing -> {
                    existing.markNotNew();
                    return existing;
                })
                .switchIfEmpty(Mono.defer(() -> {
                    String paymentId = UUID.randomUUID().toString();

                    PaymentEntity payment = new PaymentEntity(
                            paymentId,
                            ev.orderId(),
                            ev.checkoutId(),
                            ev.customerId(),
                            PaymentStatus.PENDING,
                            ev.amount(),
                            ev.currency(),
                            PaymentProvider.MOCK,
                            null,
                            null,
                            null,
                            null
                    );

                    return paymentRepository.save(payment);
                }));
    }

        private Mono<Integer> createAttempt(String paymentId, PaymentRequested ev) {
                boolean providerEnabled = paymentProviderProperties.enabled();

                return paymentRepository
                                .findMaxAttemptNo(paymentId)
                                .defaultIfEmpty(0)
                                .map(max -> max + 1)
                                .flatMap(attemptNo -> {
                                        if (providerEnabled) {
                                                return paymentRepository
                                                                .insertAttempt(paymentId, attemptNo, PaymentStatus.PENDING, null)
                                                                .thenReturn(attemptNo);
                                        }

                                        boolean fail = "FAIL".equalsIgnoreCase(ev.currency());
                                        String status = fail ? PaymentStatus.FAILED : PaymentStatus.SUCCEEDED;
                                        String reason = fail ? "Forced FAIL for testing" : null;

                                        return paymentRepository
                                                        .insertAttempt(paymentId, attemptNo, status, reason)
                                                        .thenReturn(attemptNo);
                                })
                                .onErrorResume(DuplicateKeyException.class, e ->
                                                // In case of concurrent inserts, recompute once and retry.
                                                paymentRepository
                                                                .findMaxAttemptNo(paymentId)
                                                                .defaultIfEmpty(0)
                                                                .map(max -> max + 1)
                                                                .flatMap(attemptNo -> {
                                                                        if (providerEnabled) {
                                                                                return paymentRepository
                                                                                                .insertAttempt(paymentId, attemptNo, PaymentStatus.PENDING, null)
                                                                                                .thenReturn(attemptNo);
                                                                        }

                                                                        boolean fail = "FAIL".equalsIgnoreCase(ev.currency());
                                                                        String status = fail ? PaymentStatus.FAILED : PaymentStatus.SUCCEEDED;
                                                                        String reason = fail ? "Forced FAIL for testing" : null;

                                                                        return paymentRepository
                                                                                        .insertAttempt(paymentId, attemptNo, status, reason)
                                                                                        .thenReturn(attemptNo);
                                                                })
                                );
        }

        private Mono<Void> attemptPayment(PaymentEntity saved, EventEnvelope<PaymentRequested> envelope, PaymentRequested ev, int attemptNo) {
                if (!paymentProviderProperties.enabled()) {
                        if ("FAIL".equalsIgnoreCase(ev.currency())) {
                                return Mono.error(new RuntimeException("Forced FAIL for testing"));
                        }

                        return markSucceededAndPublishCompleted(saved, envelope, ev);
                }

                String correlationId = envelope.correlationId() == null ? UUID.randomUUID().toString() : envelope.correlationId().toString();

                // On retry, reset payment back to PENDING until webhook arrives.
                if (!PaymentStatus.PENDING.equals(saved.status())) {
                        saved.markPending();
                }

                // Persistable-based entities must be marked not-new before a second save,
                // otherwise Spring Data will attempt another INSERT for the same id.
                saved.markNotNew();

                return paymentRepository
                                .save(saved)
                                .then(paymentProviderClient.createPayment(
                                                correlationId,
                                                ev.orderId(),
                                                ev.checkoutId(),
                                                ev.amount(),
                                                ev.currency(),
                                                attemptNo
                                ))
                                .flatMap(resp -> paymentAttemptRepository
                                                .setProviderPaymentId(saved.id(), attemptNo, resp.providerPaymentId())
                                                .then()
                                )
                                .then();
        }

    private Mono<Void> markSucceededAndPublishCompleted(PaymentEntity saved, EventEnvelope<PaymentRequested> envelope, PaymentRequested ev) {
        saved.markSucceeded("MOCK-" + UUID.randomUUID());
        saved.markNotNew();

        return paymentRepository
                .save(saved)
                .then(publishCompleted(envelope, ev, saved.id()));
    }

    private Mono<Void> markFailed(PaymentEntity saved, EventEnvelope<PaymentRequested> envelope, PaymentRequested ev, int attemptNo, String reason) {
        String safeReason = (reason == null || reason.isBlank()) ? "UNKNOWN" : reason;

        saved.markFailed(safeReason);
        saved.markNotNew();

        Mono<Void> updateAttempt = paymentProviderProperties.enabled()
                ? paymentAttemptRepository
                        .updateStatusAndReasonByPaymentIdAndAttemptNo(saved.id(), attemptNo, PaymentStatus.FAILED, safeReason)
                        .then()
                : Mono.empty();

        return updateAttempt
                .then(paymentRepository.save(saved))
                .then(publishFailed(envelope, ev, saved.id(), safeReason));
    }

    private Mono<Void> publishCompleted(EventEnvelope<PaymentRequested> envelope, PaymentRequested ev, String paymentId) {
        return Mono.fromCallable(() ->
                        objectMapper.writeValueAsString(
                                new EventEnvelope<>(
                                        UUID.randomUUID(),
                                        envelope.correlationId(),
                                        envelope.messageId(),
                                        EventTypes.PAYMENT_COMPLETED,
                                        1,
                                        Instant.now(),
                                        ProducerNames.PAYMENT_SERVICE,
                                        ev.orderId(),
                                        new PaymentCompleted(
                                                paymentId,
                                                ev.checkoutId(),
                                                ev.orderId(),
                                                ev.amount(),
                                                ev.currency()
                                        )
                                )
                        )
                )
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(json -> paymentEventPublisher.publishPaymentEvent(ev.orderId(), json));
    }

    private Mono<Void> publishFailed(EventEnvelope<PaymentRequested> envelope, PaymentRequested ev, String paymentId, String reason) {
        return Mono.fromCallable(() ->
                        objectMapper.writeValueAsString(
                                new EventEnvelope<>(
                                        UUID.randomUUID(),
                                        envelope.correlationId(),
                                        envelope.messageId(),
                                        EventTypes.PAYMENT_FAILED,
                                        1,
                                        Instant.now(),
                                        ProducerNames.PAYMENT_SERVICE,
                                        ev.orderId(),
                                        new PaymentFailed(
                                                paymentId,
                                                ev.checkoutId(),
                                                ev.orderId(),
                                                reason
                                        )
                                )
                        )
                )
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(json -> paymentEventPublisher.publishPaymentEvent(ev.orderId(), json));
    }
}