package rs.master.o2c.checkout.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import rs.master.o2c.checkout.api.dto.CheckoutStatusDto;
import rs.master.o2c.checkout.service.CheckoutQueryService;

@RestController
@RequestMapping("/checkouts")
public class CheckoutStatusController {

    private final CheckoutQueryService checkoutQueryService;

    public CheckoutStatusController(CheckoutQueryService checkoutQueryService) {
        this.checkoutQueryService = checkoutQueryService;
    }

    @GetMapping("/status")
    public Flux<CheckoutStatusDto> status(@RequestParam("orderIds") String orderIds) {
        return checkoutQueryService.status(orderIds);
    }
}
