package rs.master.o2c.payment.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import rs.master.o2c.payment.config.SecurityConfig;
import rs.master.o2c.payment.observability.CorrelationIdWebFilter;
import rs.master.o2c.payment.persistence.entity.PaymentAttemptEntity;
import rs.master.o2c.payment.persistence.entity.PaymentEntity;
import rs.master.o2c.payment.persistence.repository.PaymentAttemptRepository;
import rs.master.o2c.payment.persistence.repository.PaymentRepository;
import rs.master.o2c.payment.impl.PaymentQueryServiceImpl;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

@WebFluxTest(controllers = PaymentTimelineController.class)
@Import({SecurityConfig.class, CorrelationIdWebFilter.class, PaymentQueryServiceImpl.class})
@SuppressWarnings({"null", "removal"})
class PaymentTimelineControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private PaymentRepository paymentRepository;

    @MockBean
    private PaymentAttemptRepository paymentAttemptRepository;

        @MockBean
        private ReactiveJwtDecoder reactiveJwtDecoder;

    @Test
    void timeline_shouldReturn400_whenOrderIdInvalid() {
        webTestClient.mutateWith(mockJwt()).get()
                .uri("/payments/not-a-uuid/timeline")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void timeline_shouldReturn404_whenPaymentNotFound() {
        String orderId = UUID.randomUUID().toString();

        when(paymentRepository.findByOrderId(orderId)).thenReturn(Mono.empty());

        webTestClient.mutateWith(mockJwt()).get()
                .uri("/payments/{orderId}/timeline", orderId)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void timeline_shouldReturnCreatedAttemptAndTerminalEvents_sortedByTime() {
        String orderId = UUID.randomUUID().toString();

        PaymentEntity payment = new PaymentEntity(
                "pay-1",
                orderId,
                "chk-1",
                "cust-1",
                "FAILED",
                BigDecimal.ZERO,
                "USD",
                "mock",
                null,
                "PAYMENT_FAIL",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:10Z")
        );

        PaymentAttemptEntity attempt1 = new PaymentAttemptEntity(
                1L,
                "pay-1",
                1,
                "FAILED",
                "DECLINED",
                null,
                Instant.parse("2026-01-01T00:00:05Z"),
                Instant.parse("2026-01-01T00:00:06Z")
        );

        when(paymentRepository.findByOrderId(orderId)).thenReturn(Mono.just(payment));
        when(paymentAttemptRepository.findByPaymentIdOrderByAttemptNoAsc("pay-1")).thenReturn(Flux.just(attempt1));

        webTestClient.mutateWith(mockJwt()).get()
                .uri("/payments/{orderId}/timeline", orderId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].type").isEqualTo("PAYMENT_CREATED")
                .jsonPath("$[0].status").isEqualTo("FAILED")
                .jsonPath("$[0].at").isEqualTo("2026-01-01T00:00:00Z")
                .jsonPath("$[1].type").isEqualTo("PAYMENT_ATTEMPT_1")
                .jsonPath("$[1].status").isEqualTo("FAILED")
                .jsonPath("$[1].at").isEqualTo("2026-01-01T00:00:05Z")
                .jsonPath("$[1].failureReason").isEqualTo("DECLINED")
                .jsonPath("$[2].type").isEqualTo("PAYMENT_FAILED")
                .jsonPath("$[2].status").isEqualTo("FAILED")
                .jsonPath("$[2].at").isEqualTo("2026-01-01T00:00:10Z")
                .jsonPath("$[2].failureReason").isEqualTo("PAYMENT_FAIL");
    }
}
