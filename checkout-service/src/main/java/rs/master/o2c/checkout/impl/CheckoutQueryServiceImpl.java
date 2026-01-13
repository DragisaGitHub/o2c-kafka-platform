package rs.master.o2c.checkout.impl;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import rs.master.o2c.checkout.api.dto.CheckoutStatusDto;
import rs.master.o2c.checkout.api.dto.CheckoutTimelineEventDto;
import rs.master.o2c.checkout.persistence.entity.CheckoutEntity;
import rs.master.o2c.checkout.persistence.repository.CheckoutRepository;
import rs.master.o2c.checkout.service.CheckoutQueryService;
import rs.master.o2c.events.checkout.CheckoutStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class CheckoutQueryServiceImpl implements CheckoutQueryService {

    private final CheckoutRepository checkoutRepository;

    public CheckoutQueryServiceImpl(CheckoutRepository checkoutRepository) {
        this.checkoutRepository = checkoutRepository;
    }

    @Override
    public Flux<CheckoutStatusDto> status(String orderIds) {
        List<String> parsed = parseOrderIds(orderIds);
        return checkoutRepository.findByOrderIdIn(parsed)
                .map(e -> new CheckoutStatusDto(e.orderId(), e.status()));
    }

    @Override
    public Flux<CheckoutTimelineEventDto> timeline(String orderId) {
        String normalizedOrderId = normalizeOrderId(orderId);

        return checkoutRepository
                .findByOrderId(normalizedOrderId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "checkout not found")))
                .flatMapMany(CheckoutQueryServiceImpl::toTimeline);
    }

    private static Flux<CheckoutTimelineEventDto> toTimeline(CheckoutEntity checkout) {
        Flux<CheckoutTimelineEventDto> created = Flux.just(
                new CheckoutTimelineEventDto(
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
            return created.concatWith(Flux.just(new CheckoutTimelineEventDto("CHECKOUT_COMPLETED", status, updatedAt)));
        }

        if (CheckoutStatus.FAILED.equals(status)) {
            return created.concatWith(Flux.just(new CheckoutTimelineEventDto("CHECKOUT_FAILED", status, updatedAt)));
        }

        return created;
    }

    private static List<String> parseOrderIds(String orderIds) {
        if (orderIds == null || orderIds.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "orderIds is required");
        }

        String[] parts = orderIds.split(",");
        Set<String> distinct = new LinkedHashSet<>();
        for (String raw : parts) {
            String trimmed = raw == null ? "" : raw.trim();
            if (trimmed.isEmpty()) continue;
            try {
                UUID.fromString(trimmed);
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "orderIds must be UUIDs");
            }
            distinct.add(trimmed);
        }

        if (distinct.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "orderIds must not be empty");
        }

        return new ArrayList<>(distinct);
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
}
