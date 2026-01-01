package rs.master.o2c.order.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import rs.master.o2c.order.api.dto.OrderSummaryDto;
import rs.master.o2c.order.persistence.entity.OrderEntity;
import rs.master.o2c.order.persistence.repository.OrderRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

@RestController
@RequestMapping("/orders")
public class OrderQueryController {

    private final OrderRepository orderRepository;

    public OrderQueryController(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @GetMapping
    public Flux<OrderSummaryDto> list(
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate,
            @RequestParam(required = false, defaultValue = "50") int limit,
            @RequestParam(required = false) String cursor
    ) {
        if (limit <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be positive");
        }
        if (limit > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be <= 200");
        }

        String normalizedCustomerId = normalizeCustomerId(customerId);
        Instant cursorInstant = parseCursor(cursor);

        Instant fromInstant = fromDate == null ? null : fromDate.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant toExclusive = toDate == null ? null : toDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        Flux<OrderEntity> base = (normalizedCustomerId == null)
                ? orderRepository.findAll()
                : orderRepository.findByCustomerId(normalizedCustomerId);

        return base
                .filter(o -> cursorInstant == null || (o.createdAt() != null && o.createdAt().isBefore(cursorInstant)))
                .filter(o -> fromInstant == null || (o.createdAt() != null && !o.createdAt().isBefore(fromInstant)))
                .filter(o -> toExclusive == null || (o.createdAt() != null && o.createdAt().isBefore(toExclusive)))
                .sort((a, b) -> {
                    Instant aCreated = a.createdAt();
                    Instant bCreated = b.createdAt();
                    if (aCreated == null && bCreated == null) return 0;
                    if (aCreated == null) return 1;
                    if (bCreated == null) return -1;
                    return bCreated.compareTo(aCreated);
                })
                .take(limit)
                .map(o -> new OrderSummaryDto(
                        o.id(),
                        o.customerId(),
                        o.status(),
                        o.totalAmount(),
                        o.currency(),
                        o.createdAt()
                ));
    }

    private static String normalizeCustomerId(String customerId) {
        if (customerId == null || customerId.isBlank()) {
            return null;
        }

        try {
            UUID.fromString(customerId.trim());
            return customerId.trim();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "customerId must be a UUID");
        }
    }

    private static Instant parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }

        try {
            return Instant.parse(cursor.trim());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cursor must be an ISO-8601 instant");
        }
    }
}
