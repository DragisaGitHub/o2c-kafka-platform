package rs.master.o2c.payment.api;

import jakarta.validation.Valid;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import rs.master.o2c.events.CorrelationHeaders;
import rs.master.o2c.payment.api.dto.RetryPaymentRequest;
import java.util.UUID;
import rs.master.o2c.payment.service.PaymentRetryService;

@RestController
@RequestMapping("/payments")
public class PaymentRetryController {
    private final PaymentRetryService paymentRetryService;

    public PaymentRetryController(
            PaymentRetryService paymentRetryService
    ) {
        this.paymentRetryService = paymentRetryService;
    }

    @PostMapping("/{orderId}/retry")
    public Mono<ResponseEntity<RetryResponse>> retry(
            @PathVariable UUID orderId,
            @Valid @RequestBody RetryPaymentRequest request,
            @RequestHeader(value = CorrelationHeaders.X_CORRELATION_ID, required = false) String correlationId
    ) {
        if (!orderId.equals(request.orderId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "orderId mismatch");
        }

        return paymentRetryService
                .retry(request, correlationId)
                .map(outcome -> {
                    if (outcome.alreadyAccepted()) {
                        return ResponseEntity.ok(new RetryResponse("ALREADY_ACCEPTED", outcome.retryRequestId()));
                    }
                    return ResponseEntity.accepted().body(new RetryResponse("ACCEPTED", outcome.retryRequestId()));
                });
    }

    public record RetryResponse(String status, UUID retryRequestId) {}
}
