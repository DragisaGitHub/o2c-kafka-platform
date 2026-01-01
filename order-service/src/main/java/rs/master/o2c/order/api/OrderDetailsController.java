package rs.master.o2c.order.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import rs.master.o2c.order.api.dto.OrderDetailsDto;
import rs.master.o2c.order.persistence.entity.OrderEntity;
import rs.master.o2c.order.persistence.repository.OrderRepository;

import java.util.UUID;

@RestController
@RequestMapping("/orders")
public class OrderDetailsController {

    private final OrderRepository orderRepository;

    public OrderDetailsController(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @GetMapping("/{orderId}")
    public Mono<OrderDetailsDto> get(@PathVariable String orderId) {
        String normalizedOrderId = normalizeOrderId(orderId);

        return orderRepository
                .findById(normalizedOrderId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found")))
                .map(OrderDetailsController::toDto);
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

    private static OrderDetailsDto toDto(OrderEntity o) {
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
}
