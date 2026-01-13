package rs.master.o2c.order.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import rs.master.o2c.order.api.dto.OrderDetailsDto;
import rs.master.o2c.order.service.OrderReadService;

@RestController
@RequestMapping("/orders")
public class OrderDetailsController {

    private final OrderReadService orderReadService;

    public OrderDetailsController(OrderReadService orderReadService) {
        this.orderReadService = orderReadService;
    }

    @GetMapping("/{orderId}")
    public Mono<OrderDetailsDto> get(@PathVariable String orderId) {
        return orderReadService.get(orderId);
    }
}
