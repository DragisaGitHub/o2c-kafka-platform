package rs.master.o2c.payment.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import rs.master.o2c.events.CorrelationHeaders;
import rs.master.o2c.events.payment.PaymentStatus;
import rs.master.o2c.payment.service.ProviderWebhookService;

import java.util.UUID;

@RestController
public class ProviderWebhookController {

    private final ProviderWebhookService providerWebhookService;

    public ProviderWebhookController(
            ProviderWebhookService providerWebhookService
    ) {
        this.providerWebhookService = providerWebhookService;
    }

    @PostMapping(path = "/webhooks/provider/payments", consumes = MediaType.APPLICATION_JSON_VALUE)
        @ResponseStatus(HttpStatus.ACCEPTED)
    public Mono<Void> handleWebhook(
            @Valid @RequestBody ProviderPaymentWebhookRequest request,
            @RequestHeader(value = CorrelationHeaders.X_CORRELATION_ID, required = false) String correlationIdHeader
    ) {
        UUID providerPaymentId = parseProviderPaymentId(request.providerPaymentId());
        String status = normalizeStatus(request.status());
        String correlationId = (correlationIdHeader == null || correlationIdHeader.isBlank())
                ? UUID.randomUUID().toString()
                : correlationIdHeader.trim();

        return providerWebhookService
            .handleWebhook(providerPaymentId, status, request.failureReason(), correlationId);
        }

        private static UUID parseProviderPaymentId(String providerPaymentId) {
        if (providerPaymentId == null || providerPaymentId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "providerPaymentId is required");
        }

        try {
            return UUID.fromString(providerPaymentId.trim());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "providerPaymentId must be a UUID");
        }
    }

    private static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status is required");
        }

        String s = status.trim().toUpperCase();
        if (!PaymentStatus.SUCCEEDED.equals(s) && !PaymentStatus.FAILED.equals(s)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status must be SUCCEEDED or FAILED");
        }
        return s;
    }

    public record ProviderPaymentWebhookRequest(
            @NotBlank String providerPaymentId,
            @NotBlank String status,
            String failureReason
    ) {
    }
}
