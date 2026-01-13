package rs.master.o2c.payment.api;

import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.SenderResult;
import rs.master.o2c.events.CorrelationHeaders;
import rs.master.o2c.payment.api.dto.RetryPaymentRequest;
import rs.master.o2c.payment.config.SecurityConfig;
import rs.master.o2c.payment.observability.CorrelationIdWebFilter;
import rs.master.o2c.payment.impl.PaymentRetryServiceImpl;
import org.apache.kafka.clients.producer.ProducerRecord;

import org.springframework.r2dbc.core.DatabaseClient;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

@WebFluxTest(controllers = PaymentRetryController.class)
@Import({SecurityConfig.class, CorrelationIdWebFilter.class, PaymentRetryServiceImpl.class})
@SuppressWarnings({"null", "removal"})
class PaymentRetryControllerTest {

    @Autowired
    private WebTestClient webTestClient;

        @MockBean(answer = Answers.RETURNS_DEEP_STUBS)
    private DatabaseClient databaseClient;

    @MockBean
    private ReactiveKafkaProducerTemplate<String, String> producer;

        @MockBean
        private ReactiveJwtDecoder reactiveJwtDecoder;

    @Test
    void retry_shouldPublishOnce_andBeIdempotentByRetryRequestId() {
        UUID orderId = UUID.randomUUID();
        UUID retryRequestId = UUID.randomUUID();

        Map<String, Object> paymentRow = new HashMap<>();
        paymentRow.put("checkoutId", UUID.randomUUID().toString());
        paymentRow.put("customerId", UUID.randomUUID().toString());
        paymentRow.put("amount", new BigDecimal("12.34"));
        paymentRow.put("currency", "USD");

        when(databaseClient.sql(contains("from payment"))
                .bind(eq("orderId"), anyString())
                .fetch()
                .one())
                .thenReturn(Mono.just(paymentRow));

        // INSERT idempotency row (first call succeeds, second call duplicates)
        when(databaseClient.sql(contains("insert into payment_retry_request"))
                .bind(eq("retryRequestId"), anyString())
                .bind(eq("orderId"), anyString())
                .fetch()
                .rowsUpdated())
                .thenReturn(Mono.just(1L))
                .thenReturn(Mono.error(new DuplicateKeyException("dup")));

        @SuppressWarnings("unchecked")
        SenderResult<Void> senderResult = (SenderResult<Void>) mock(SenderResult.class);
        when(producer.send(ArgumentMatchers.<ProducerRecord<String, String>>any()))
                .thenReturn(Mono.just(senderResult));

        RetryPaymentRequest body = new RetryPaymentRequest(orderId, retryRequestId);

        // First call => ACCEPTED (202) and publish
        webTestClient.mutateWith(mockJwt()).post()
                .uri("/payments/{orderId}/retry", orderId)
                .header(CorrelationHeaders.X_CORRELATION_ID, UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isAccepted()
                .expectBody()
                .jsonPath("$.status").isEqualTo("ACCEPTED")
                .jsonPath("$.retryRequestId").isEqualTo(retryRequestId.toString());

        // Second call with same retryRequestId => OK (200), no publish
        webTestClient.mutateWith(mockJwt()).post()
                .uri("/payments/{orderId}/retry", orderId)
                .header(CorrelationHeaders.X_CORRELATION_ID, UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("ALREADY_ACCEPTED")
                .jsonPath("$.retryRequestId").isEqualTo(retryRequestId.toString());

                verify(producer, times(1)).send(ArgumentMatchers.<ProducerRecord<String, String>>any());
    }
}
