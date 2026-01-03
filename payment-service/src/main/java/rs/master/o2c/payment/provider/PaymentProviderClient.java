package rs.master.o2c.payment.provider;

import java.math.BigDecimal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import rs.master.o2c.events.CorrelationHeaders;

@Slf4j
@Component
public class PaymentProviderClient {

    private final WebClient providerWebClient;

    public PaymentProviderClient(WebClient providerWebClient) {
        this.providerWebClient = providerWebClient;
    }

    public Mono<CreatePaymentResponse> createPayment(
            String correlationId,
            String orderId,
            String checkoutId,
            BigDecimal amount,
            String currency,
            int attemptNo
    ) {
        return providerWebClient
                .post()
                .uri("/provider/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .header(CorrelationHeaders.X_CORRELATION_ID, correlationId)
                .bodyValue(new CreatePaymentRequest(orderId, checkoutId, amount, currency, attemptNo))
                .retrieve()
                .bodyToMono(CreatePaymentResponse.class)
                .doOnSuccess(resp -> log.info(
                        "provider createPayment accepted providerPaymentId={} correlationId={} orderId={} checkoutId={} attemptNo={}",
                        resp.providerPaymentId(),
                        correlationId,
                        orderId,
                        checkoutId,
                        attemptNo
                ));
    }

    public record CreatePaymentRequest(
            String orderId,
            String checkoutId,
            BigDecimal amount,
            String currency,
            Integer attemptNo
    ) {
    }

    public record CreatePaymentResponse(
            String providerPaymentId,
            String status
    ) {
    }
}
