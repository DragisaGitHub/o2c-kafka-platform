package rs.master.o2c.checkout.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import rs.master.o2c.checkout.api.dto.CheckoutTimelineEventDto;
import rs.master.o2c.checkout.service.CheckoutQueryService;

@RestController
@RequestMapping("/checkouts")
public class CheckoutTimelineController {

    private final CheckoutQueryService checkoutQueryService;

    public CheckoutTimelineController(CheckoutQueryService checkoutQueryService) {
        this.checkoutQueryService = checkoutQueryService;
    }

    @GetMapping("/{orderId}/timeline")
    public Flux<CheckoutTimelineEventDto> timeline(@PathVariable String orderId) {
        return checkoutQueryService.timeline(orderId);
    }
}
