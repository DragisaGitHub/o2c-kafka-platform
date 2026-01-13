package rs.master.o2c.order.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import rs.master.o2c.order.api.dto.OrderSummaryDto;
import rs.master.o2c.order.service.OrderReadService;
import java.time.LocalDate;

@RestController
@RequestMapping("/orders")
public class OrderQueryController {

    private final OrderReadService orderReadService;

    public OrderQueryController(OrderReadService orderReadService) {
        this.orderReadService = orderReadService;
    }

    @GetMapping
    public Flux<OrderSummaryDto> list(
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate,
            @RequestParam(required = false, defaultValue = "50") int limit,
            @RequestParam(required = false) String cursor
    ) {
        return orderReadService.list(customerId, fromDate, toDate, limit, cursor);
    }
}
