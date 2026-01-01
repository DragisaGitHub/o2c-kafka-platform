package rs.master.o2c.order.api;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import rs.master.o2c.order.api.dto.CreateOrderRequest;
import rs.master.o2c.order.api.dto.CreateOrderResponse;
import rs.master.o2c.order.domain.service.OrderService;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public Mono<CreateOrderResponse> create(@RequestBody CreateOrderRequest request) {
        return orderService.create(request)
                .map(o -> new CreateOrderResponse(UUID.fromString(o.id())));
    }
}