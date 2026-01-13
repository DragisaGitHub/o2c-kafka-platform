package rs.master.o2c.checkout.service;

import reactor.core.publisher.Flux;
import rs.master.o2c.checkout.api.dto.CheckoutStatusDto;
import rs.master.o2c.checkout.api.dto.CheckoutTimelineEventDto;

public interface CheckoutQueryService {

    Flux<CheckoutStatusDto> status(String orderIds);

    Flux<CheckoutTimelineEventDto> timeline(String orderId);
}
