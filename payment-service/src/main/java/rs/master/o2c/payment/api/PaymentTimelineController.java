package rs.master.o2c.payment.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import rs.master.o2c.payment.persistence.entity.PaymentEntity;
import rs.master.o2c.payment.persistence.repository.PaymentAttemptRepository;
import rs.master.o2c.payment.persistence.repository.PaymentRepository;

import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;

@RestController
@RequestMapping("/payments")
public class PaymentTimelineController {

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;

    public PaymentTimelineController(
            PaymentRepository paymentRepository,
            PaymentAttemptRepository paymentAttemptRepository
    ) {
        this.paymentRepository = paymentRepository;
        this.paymentAttemptRepository = paymentAttemptRepository;
    }

    @GetMapping("/{orderId}/timeline")
    public Flux<TimelineEventDto> timeline(@PathVariable String orderId) {
        String normalizedOrderId = normalizeOrderId(orderId);

        return paymentRepository
            .findByOrderId(normalizedOrderId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "payment not found")))
                .flatMapMany(payment -> {
                    Flux<TimelineEventDto> paymentCreated = Flux.just(
                            new TimelineEventDto(
                                    "PAYMENT_CREATED",
                                    payment.status(),
                                    payment.createdAt(),
                                    null
                            )
                    );

                    Flux<TimelineEventDto> attempts = paymentAttemptRepository
                            .findByPaymentIdOrderByAttemptNoAsc(payment.id())
                            .map(a -> new TimelineEventDto(
                                "PAYMENT_ATTEMPT_" + a.attemptNo(),
                                a.status(),
                                a.createdAt(),
                                a.reason()
                            ));

                    Flux<TimelineEventDto> terminal = buildTerminalEvent(payment);

                    return Flux
                            .concat(paymentCreated, attempts, terminal)
                            .sort(Comparator.comparing(
                                TimelineEventDto::at,
                                Comparator.nullsLast(Comparator.naturalOrder())
                            ));
                });
    }

    private static Flux<TimelineEventDto> buildTerminalEvent(PaymentEntity payment) {
        Instant updatedAt = payment.updatedAt();
        if (updatedAt == null) {
            return Flux.empty();
        }

        String status = payment.status();
        if ("SUCCEEDED".equals(status)) {
            return Flux.just(new TimelineEventDto("PAYMENT_SUCCEEDED", status, updatedAt, null));
        }
        if ("FAILED".equals(status)) {
            return Flux.just(new TimelineEventDto("PAYMENT_FAILED", status, updatedAt, payment.failureReason()));
        }

        return Flux.empty();
    }

    private static String normalizeOrderId(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "orderId is required");
        }

        try {
            UUID.fromString(orderId.trim());
            return orderId.trim();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "orderId must be a UUID");
        }
    }

    public record TimelineEventDto(String type, String status, Instant at, String failureReason) {}
}
