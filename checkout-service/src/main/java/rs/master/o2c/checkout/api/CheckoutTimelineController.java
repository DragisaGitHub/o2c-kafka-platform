package rs.master.o2c.checkout.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import rs.master.o2c.checkout.persistence.entity.CheckoutEntity;
import rs.master.o2c.checkout.persistence.repository.CheckoutRepository;
import rs.master.o2c.events.checkout.CheckoutStatus;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/checkouts")
public class CheckoutTimelineController {

    private final CheckoutRepository checkoutRepository;

    public CheckoutTimelineController(CheckoutRepository checkoutRepository) {
        this.checkoutRepository = checkoutRepository;
    }

    @GetMapping("/{orderId}/timeline")
    public Flux<TimelineEventDto> timeline(@PathVariable String orderId) {
        String normalizedOrderId = normalizeOrderId(orderId);

        return checkoutRepository
                .findByOrderId(normalizedOrderId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "checkout not found")))
                .flatMapMany(CheckoutTimelineController::toTimeline);
    }

    private static Flux<TimelineEventDto> toTimeline(CheckoutEntity checkout) {
        Flux<TimelineEventDto> created = Flux.just(
                new TimelineEventDto(
                        "CHECKOUT_CREATED",
                        CheckoutStatus.PENDING,
                        checkout.createdAt()
                )
        );

        Instant updatedAt = checkout.updatedAt();
        String status = checkout.status();

        if (updatedAt == null) {
            return created;
        }

        if (CheckoutStatus.COMPLETED.equals(status)) {
            return created.concatWith(Flux.just(new TimelineEventDto("CHECKOUT_COMPLETED", status, updatedAt)));
        }

        if (CheckoutStatus.FAILED.equals(status)) {
            return created.concatWith(Flux.just(new TimelineEventDto("CHECKOUT_FAILED", status, updatedAt)));
        }

        return created;
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

    public record TimelineEventDto(String type, String status, Instant at) {}
}
