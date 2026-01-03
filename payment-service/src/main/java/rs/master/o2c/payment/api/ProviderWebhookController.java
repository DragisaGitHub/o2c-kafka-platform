package rs.master.o2c.payment.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import rs.master.o2c.events.CorrelationHeaders;
import rs.master.o2c.events.EventEnvelope;
import rs.master.o2c.events.EventTypes;
import rs.master.o2c.events.ProducerNames;
import rs.master.o2c.events.payment.PaymentCompleted;
import rs.master.o2c.events.payment.PaymentFailed;
import rs.master.o2c.events.payment.PaymentRequested;
import rs.master.o2c.events.payment.PaymentStatus;
import rs.master.o2c.payment.kafka.PaymentEventPublisher;
import rs.master.o2c.payment.persistence.entity.PaymentAttemptEntity;
import rs.master.o2c.payment.persistence.entity.PaymentEntity;
import rs.master.o2c.payment.persistence.repository.PaymentAttemptRepository;
import rs.master.o2c.payment.persistence.repository.PaymentRepository;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@RestController
public class ProviderWebhookController {

    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentEventPublisher paymentEventPublisher;
    private final ObjectMapper objectMapper;

    public ProviderWebhookController(
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

    @PostMapping(path = "/webhooks/provider/payments", consumes = MediaType.APPLICATION_JSON_VALUE)
        @ResponseStatus(HttpStatus.ACCEPTED)
    public Mono<Void> handleWebhook(
            @Valid @RequestBody ProviderPaymentWebhookRequest request,
            @RequestHeader(value = CorrelationHeaders.X_CORRELATION_ID, required = false) String correlationIdHeader
    ) {
        UUID providerPaymentId = parseProviderPaymentId(request.providerPaymentId());
        String status = normalizeStatus(request.status());
        String correlationId = (correlationIdHeader == null || correlationIdHeader.isBlank())
                ? UUID.randomUUID().toString()
                : correlationIdHeader.trim();

        return paymentAttemptRepository
                .findByProviderPaymentId(providerPaymentId.toString())
                .flatMap(attempt -> processKnownAttempt(attempt, providerPaymentId, status, request.failureReason(), correlationId))
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

    private static UUID parseProviderPaymentId(String providerPaymentId) {
        if (providerPaymentId == null || providerPaymentId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "providerPaymentId is required");
        }

        try {
            return UUID.fromString(providerPaymentId.trim());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "providerPaymentId must be a UUID");
        }
    }

    private static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status is required");
        }

        String s = status.trim().toUpperCase();
        if (!PaymentStatus.SUCCEEDED.equals(s) && !PaymentStatus.FAILED.equals(s)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status must be SUCCEEDED or FAILED");
        }
        return s;
    }

    private static UUID parseUuidOrNull(String v) {
        if (v == null || v.isBlank()) return null;
        try {
            return UUID.fromString(v.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public record ProviderPaymentWebhookRequest(
            @NotBlank String providerPaymentId,
            @NotBlank String status,
            String failureReason
    ) {
    }
}
