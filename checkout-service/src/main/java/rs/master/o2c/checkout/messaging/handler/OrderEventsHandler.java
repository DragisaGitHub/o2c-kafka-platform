package rs.master.o2c.checkout.messaging.handler;

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
import rs.master.o2c.checkout.kafka.CheckoutEventPublisher;
import rs.master.o2c.checkout.persistence.entity.CheckoutEntity;
import rs.master.o2c.checkout.persistence.entity.InboxProcessedEntity;
import rs.master.o2c.checkout.persistence.repository.CheckoutRepository;
import rs.master.o2c.checkout.persistence.repository.InboxProcessedRepository;
import rs.master.o2c.events.*;
import rs.master.o2c.events.checkout.CheckoutCompleted;
import rs.master.o2c.events.checkout.CheckoutFailed;
import rs.master.o2c.events.checkout.CheckoutStatus;
import rs.master.o2c.events.order.OrderCreated;

@Component
@RequiredArgsConstructor
public class OrderEventsHandler {

    private final ObjectMapper objectMapper;
    private final CheckoutRepository checkoutRepository;
    private final InboxProcessedRepository inboxProcessedRepository;
    private final CheckoutEventPublisher checkoutEventPublisher;

    public Mono<Void> handle(String payload) {
        return Mono.fromCallable(() ->
                        objectMapper.readValue(payload, new TypeReference<EventEnvelope<OrderCreated>>() {})
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

    private Mono<Void> process(EventEnvelope<OrderCreated> envelope) {
        if (!EventTypes.ORDER_CREATED.equals(envelope.eventType())) {
            return Mono.empty();
        }

        OrderCreated ev = envelope.payload();
        String checkoutId = UUID.randomUUID().toString();

        CheckoutEntity checkout = new CheckoutEntity(
                checkoutId,
                ev.orderId(),
                ev.customerId(),
                CheckoutStatus.PENDING,
                ev.total().amount(),
                ev.total().currency(),
                Instant.now()
        );

        return checkoutRepository
                .save(checkout)
                .flatMap(saved ->
                        attemptCheckout(saved, envelope, ev)
                                .onErrorResume(ex ->
                                        markFailed(saved, envelope, ev, ex.getMessage())
                                )
                )
                .onErrorResume(DataIntegrityViolationException.class, e -> Mono.empty())
                .then();
    }

    private Mono<Void> attemptCheckout(CheckoutEntity saved, EventEnvelope<OrderCreated> envelope, OrderCreated ev) {
        if ("FAIL".equalsIgnoreCase(ev.total().currency())) {
            return Mono.error(new RuntimeException("Forced FAIL for testing"));
        }

                saved.markCompleted();
                saved.markNotNew();

                return checkoutRepository
                                .save(saved)
                                .then(publishCompleted(envelope, ev, saved.id()));
    }

    private Mono<Void> markFailed(CheckoutEntity saved, EventEnvelope<OrderCreated> envelope, OrderCreated ev, String reason) {
        saved.markFailed();
        saved.markNotNew();

        return checkoutRepository
                .save(saved)
                .then(publishFailed(envelope, ev, saved.id(), reason));
    }

    private Mono<Void> publishCompleted(EventEnvelope<OrderCreated> envelope, OrderCreated ev, String checkoutId) {
        return Mono.fromCallable(() ->
                        objectMapper.writeValueAsString(
                                new EventEnvelope<>(
                                        UUID.randomUUID(),
                                        envelope.correlationId(),
                                        envelope.messageId(),
                                        EventTypes.CHECKOUT_COMPLETED,
                                        1,
                                        Instant.now(),
                                        ProducerNames.CHECKOUT_SERVICE,
                                        ev.orderId(),
                                        new CheckoutCompleted(
                                                checkoutId,
                                                ev.orderId(),
                                                ev.customerId()
                                        )
                                )
                        )
                )
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(json -> checkoutEventPublisher.publishCheckoutEvent(ev.orderId(), json));
    }

    private Mono<Void> publishFailed(EventEnvelope<OrderCreated> envelope, OrderCreated ev, String checkoutId, String reason) {
        String safeReason = (reason == null || reason.isBlank()) ? "UNKNOWN" : reason;

        return Mono.fromCallable(() ->
                        objectMapper.writeValueAsString(
                                new EventEnvelope<>(
                                        UUID.randomUUID(),
                                        envelope.correlationId(),
                                        envelope.messageId(),
                                        EventTypes.CHECKOUT_FAILED,
                                        1,
                                        Instant.now(),
                                        ProducerNames.CHECKOUT_SERVICE,
                                        ev.orderId(),
                                        new CheckoutFailed(
                                                checkoutId,
                                                ev.orderId(),
                                                safeReason
                                        )
                                )
                        )
                )
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(json -> checkoutEventPublisher.publishCheckoutEvent(ev.orderId(), json));
    }
}