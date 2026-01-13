package rs.master.o2c.order.impl;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import rs.master.o2c.order.api.dto.OrderDetailsDto;
import rs.master.o2c.order.api.dto.OrderSummaryDto;
import rs.master.o2c.order.persistence.entity.OrderEntity;
import rs.master.o2c.order.persistence.repository.OrderRepository;
import rs.master.o2c.order.service.OrderReadService;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class OrderReadServiceImpl implements OrderReadService {

    private final OrderRepository orderRepository;

    public OrderReadServiceImpl(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public Mono<OrderDetailsDto> get(String orderId) {
        String normalizedOrderId = normalizeOrderId(orderId);

        return orderRepository
                .findById(normalizedOrderId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found")))
                .map(OrderReadServiceImpl::toDetailsDto);
    }

    @Override
    public Flux<OrderSummaryDto> list(
            String customerId,
            LocalDate fromDate,
            LocalDate toDate,
            int limit,
            String cursor
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
                .map(OrderReadServiceImpl::toSummaryDto);
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

    private static OrderDetailsDto toDetailsDto(OrderEntity o) {
        return new OrderDetailsDto(
                o.id(),
                o.customerId(),
                o.status(),
                o.totalAmount(),
                o.currency(),
                o.createdAt(),
                o.correlationId()
        );
    }

    private static OrderSummaryDto toSummaryDto(OrderEntity o) {
        return new OrderSummaryDto(
                o.id(),
                o.customerId(),
                o.status(),
                o.totalAmount(),
                o.currency(),
                o.createdAt()
        );
    }
}
