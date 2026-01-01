package rs.master.o2c.payment.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import rs.master.o2c.payment.persistence.repository.PaymentRepository;

import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;

@RestController
@RequestMapping("/payments")
public class PaymentTimelineController {

    private final PaymentRepository paymentRepository;

    public PaymentTimelineController(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @GetMapping("/{orderId}/timeline")
    public Flux<TimelineEventDto> timeline(@PathVariable String orderId) {
        String normalizedOrderId = normalizeOrderId(orderId);

        return paymentRepository
                .findTimelinePaymentByOrderId(normalizedOrderId)
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

                    Flux<TimelineEventDto> attempts = paymentRepository
                            .findAttemptsByPaymentId(payment.paymentId())
                            .map(a -> new TimelineEventDto(
                                    "PAYMENT_ATTEMPT_" + a.attemptNo(),
                                    a.status(),
                                    a.createdAt(),
                                    a.failureReason()
                            ));

                    Flux<TimelineEventDto> terminal = buildTerminalEvent(payment);

                    return Flux
                            .concat(paymentCreated, attempts, terminal)
                            .sort(Comparator.comparing(TimelineEventDto::at));
                });
    }

    private static Flux<TimelineEventDto> buildTerminalEvent(PaymentRepository.PaymentTimelineRow payment) {
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
