package rs.master.o2c.payment.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import rs.master.o2c.payment.config.SecurityConfig;
import rs.master.o2c.payment.observability.CorrelationIdWebFilter;
import rs.master.o2c.payment.persistence.repository.PaymentRepository;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.when;

@WebFluxTest(controllers = PaymentTimelineController.class)
@Import({SecurityConfig.class, CorrelationIdWebFilter.class})
@SuppressWarnings({"null", "removal"})
class PaymentTimelineControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private PaymentRepository paymentRepository;

    @Test
    void timeline_shouldReturn400_whenOrderIdInvalid() {
        webTestClient.get()
                .uri("/payments/not-a-uuid/timeline")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void timeline_shouldReturn404_whenPaymentNotFound() {
        String orderId = UUID.randomUUID().toString();

        when(paymentRepository.findTimelinePaymentByOrderId(orderId)).thenReturn(Mono.empty());

        webTestClient.get()
                .uri("/payments/{orderId}/timeline", orderId)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void timeline_shouldReturnCreatedAttemptAndTerminalEvents_sortedByTime() {
        String orderId = UUID.randomUUID().toString();

        PaymentRepository.PaymentTimelineRow payment = new PaymentRepository.PaymentTimelineRow() {
            @Override
            public String paymentId() {
                return "pay-1";
            }

            @Override
            public String status() {
                return "FAILED";
            }

            @Override
            public String failureReason() {
                return "PAYMENT_FAIL";
            }

            @Override
            public Instant createdAt() {
                return Instant.parse("2026-01-01T00:00:00Z");
            }

            @Override
            public Instant updatedAt() {
                return Instant.parse("2026-01-01T00:00:10Z");
            }
        };

        PaymentRepository.PaymentAttemptRow attempt1 = new PaymentRepository.PaymentAttemptRow() {
            @Override
            public Integer attemptNo() {
                return 1;
            }

            @Override
            public String status() {
                return "FAILED";
            }

            @Override
            public String failureReason() {
                return "DECLINED";
            }

            @Override
            public Instant createdAt() {
                return Instant.parse("2026-01-01T00:00:05Z");
            }
        };

        when(paymentRepository.findTimelinePaymentByOrderId(orderId)).thenReturn(Mono.just(payment));
        when(paymentRepository.findAttemptsByPaymentId("pay-1")).thenReturn(Flux.just(attempt1));

        webTestClient.get()
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
