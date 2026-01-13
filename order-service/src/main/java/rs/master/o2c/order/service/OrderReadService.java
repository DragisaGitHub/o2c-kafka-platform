package rs.master.o2c.order.service;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import rs.master.o2c.order.api.dto.OrderDetailsDto;
import rs.master.o2c.order.api.dto.OrderSummaryDto;

import java.time.LocalDate;

public interface OrderReadService {

    Mono<OrderDetailsDto> get(String orderId);

    Flux<OrderSummaryDto> list(
            String customerId,
            LocalDate fromDate,
            LocalDate toDate,
            int limit,
            String cursor
    );
}
