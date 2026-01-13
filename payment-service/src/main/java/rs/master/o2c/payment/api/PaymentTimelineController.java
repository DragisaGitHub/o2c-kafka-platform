package rs.master.o2c.payment.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import rs.master.o2c.payment.service.PaymentQueryService;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/payments")
public class PaymentTimelineController {

    private final PaymentQueryService paymentQueryService;

    public PaymentTimelineController(
            PaymentQueryService paymentQueryService
    ) {
        this.paymentQueryService = paymentQueryService;
    }

    @GetMapping("/{orderId}/timeline")
    public Flux<TimelineEventDto> timeline(@PathVariable String orderId) {
        String normalizedOrderId = normalizeOrderId(orderId);

        return paymentQueryService.timelineByOrderId(normalizedOrderId);
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
