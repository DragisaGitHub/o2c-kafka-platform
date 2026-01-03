package rs.master.o2c.provider.api;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import rs.master.o2c.events.CorrelationHeaders;
import rs.master.o2c.events.payment.PaymentStatus;
import rs.master.o2c.provider.config.ProviderProperties;
import rs.master.o2c.provider.scheduling.ProviderCallbackQueue;
import rs.master.o2c.provider.scheduling.ProviderCallbackTask;

@Slf4j
@RestController
@RequestMapping("/provider")
@Validated
public class ProviderPaymentsController {

    private final ProviderProperties properties;
    private final ProviderCallbackQueue callbackQueue;

    public ProviderPaymentsController(ProviderProperties properties, ProviderCallbackQueue callbackQueue) {
        this.properties = properties;
        this.callbackQueue = callbackQueue;
    }

    @PostMapping("/payments")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Mono<CreatePaymentResponse> createPayment(
            @Valid @RequestBody CreatePaymentRequest request,
            @RequestHeader(value = CorrelationHeaders.X_CORRELATION_ID, required = false) String correlationId
    ) {
        UUID providerPaymentId = UUID.randomUUID();

        String normalizedCorrelationId = (correlationId == null || correlationId.isBlank())
                ? UUID.randomUUID().toString()
                : correlationId.trim();

        boolean fail = "FAIL".equalsIgnoreCase(request.currency());
        String outcomeStatus = fail ? PaymentStatus.FAILED : PaymentStatus.SUCCEEDED;
        String failureReason = fail ? "Forced FAIL for testing" : null;

        callbackQueue.enqueue(new ProviderCallbackTask(
                properties.webhookUrl(),
                normalizedCorrelationId,
                providerPaymentId,
                outcomeStatus,
                failureReason
        ));

        log.info(
                "provider intent created providerPaymentId={} orderId={} checkoutId={} attemptNo={} outcome={} correlationId={}",
                providerPaymentId,
                request.orderId(),
                request.checkoutId(),
                request.attemptNo(),
                outcomeStatus,
                normalizedCorrelationId
        );

        return Mono.just(new CreatePaymentResponse(providerPaymentId.toString(), "ACCEPTED"));
    }

    public record CreatePaymentRequest(
            @NotBlank String orderId,
            @NotBlank String checkoutId,
            @NotNull @Positive BigDecimal amount,
            @NotBlank String currency,
            @NotNull @Positive Integer attemptNo
    ) {
    }

    public record CreatePaymentResponse(
            String providerPaymentId,
            String status
    ) {
    }
}
