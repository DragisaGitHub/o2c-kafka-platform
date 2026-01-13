package rs.master.o2c.payment.impl;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import rs.master.o2c.payment.api.PaymentTimelineController;
import rs.master.o2c.payment.api.dto.PaymentStatusDto;
import rs.master.o2c.payment.persistence.entity.PaymentEntity;
import rs.master.o2c.payment.persistence.repository.PaymentAttemptRepository;
import rs.master.o2c.payment.persistence.repository.PaymentRepository;
import rs.master.o2c.payment.service.PaymentQueryService;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Service
public class PaymentQueryServiceImpl implements PaymentQueryService {

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;

    public PaymentQueryServiceImpl(
            PaymentRepository paymentRepository,
            PaymentAttemptRepository paymentAttemptRepository
    ) {
        this.paymentRepository = paymentRepository;
        this.paymentAttemptRepository = paymentAttemptRepository;
    }

    @Override
    public Flux<PaymentStatusDto> statusByOrderIds(List<String> orderIds) {
        return paymentRepository
                .findByOrderIdIn(orderIds)
                .map(p -> new PaymentStatusDto(p.orderId(), p.status(), p.failureReason()));
    }

    @Override
    public Flux<PaymentTimelineController.TimelineEventDto> timelineByOrderId(String orderId) {
        return paymentRepository
                .findByOrderId(orderId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "payment not found")))
                .flatMapMany(payment -> {
                    Flux<PaymentTimelineController.TimelineEventDto> paymentCreated = Flux.just(
                            new PaymentTimelineController.TimelineEventDto(
                                    "PAYMENT_CREATED",
                                    payment.status(),
                                    payment.createdAt(),
                                    null
                            )
                    );

                    Flux<PaymentTimelineController.TimelineEventDto> attempts = paymentAttemptRepository
                            .findByPaymentIdOrderByAttemptNoAsc(payment.id())
                            .map(a -> new PaymentTimelineController.TimelineEventDto(
                                    "PAYMENT_ATTEMPT_" + a.attemptNo(),
                                    a.status(),
                                    a.createdAt(),
                                    a.reason()
                            ));

                    Flux<PaymentTimelineController.TimelineEventDto> terminal = buildTerminalEvent(payment);

                    return Flux
                            .concat(paymentCreated, attempts, terminal)
                            .sort(Comparator.comparing(
                                    PaymentTimelineController.TimelineEventDto::at,
                                    Comparator.nullsLast(Comparator.naturalOrder())
                            ));
                });
    }

    private static Flux<PaymentTimelineController.TimelineEventDto> buildTerminalEvent(PaymentEntity payment) {
        Instant updatedAt = payment.updatedAt();
        if (updatedAt == null) {
            return Flux.empty();
        }

        String status = payment.status();
        if ("SUCCEEDED".equals(status)) {
            return Flux.just(new PaymentTimelineController.TimelineEventDto("PAYMENT_SUCCEEDED", status, updatedAt, null));
        }
        if ("FAILED".equals(status)) {
            return Flux.just(new PaymentTimelineController.TimelineEventDto("PAYMENT_FAILED", status, updatedAt, payment.failureReason()));
        }

        return Flux.empty();
    }
}
