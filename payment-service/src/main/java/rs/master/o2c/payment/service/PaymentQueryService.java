package rs.master.o2c.payment.service;

import reactor.core.publisher.Flux;
import rs.master.o2c.payment.api.PaymentTimelineController;
import rs.master.o2c.payment.api.dto.PaymentStatusDto;

import java.util.List;

public interface PaymentQueryService {

    Flux<PaymentStatusDto> statusByOrderIds(List<String> orderIds);

    Flux<PaymentTimelineController.TimelineEventDto> timelineByOrderId(String orderId);
}
