package rs.master.o2c.payment.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import rs.master.o2c.payment.api.dto.PaymentStatusDto;
import rs.master.o2c.payment.config.SecurityConfig;
import rs.master.o2c.payment.observability.CorrelationIdWebFilter;
import rs.master.o2c.payment.persistence.entity.PaymentEntity;
import rs.master.o2c.payment.persistence.repository.PaymentRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

@WebFluxTest(controllers = PaymentStatusController.class)
@Import({SecurityConfig.class, CorrelationIdWebFilter.class})
@SuppressWarnings({"null", "removal"})
class PaymentStatusControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private PaymentRepository paymentRepository;

    @Test
    void status_shouldReturn400_whenOrderIdsMissing() {
        webTestClient.get()
                .uri("/payments/status")
                .exchange()
                .expectStatus().isBadRequest();

        verifyNoInteractions(paymentRepository);
    }

    @Test
    void status_shouldReturn400_whenOrderIdsEmpty() {
        webTestClient.get()
                .uri("/payments/status?orderIds=")
                .exchange()
                .expectStatus().isBadRequest();

        verifyNoInteractions(paymentRepository);
    }

    @Test
    void status_shouldReturn400_whenOrderIdsContainsInvalidUuid() {
        webTestClient.get()
                .uri("/payments/status?orderIds=not-a-uuid")
                .exchange()
                .expectStatus().isBadRequest();

        verifyNoInteractions(paymentRepository);
    }

    @Test
    void status_shouldReturn200_andIncludeFailureReasonPresenceOrAbsence_onHappyPath() {
        String id1 = UUID.randomUUID().toString();
        String id2 = UUID.randomUUID().toString();

        PaymentEntity p1 = new PaymentEntity(
                "p1",
                id1,
                "c1",
                "cust1",
                "FAILED",
                BigDecimal.ZERO,
                "USD",
                "mock",
                null,
                "DECLINED",
                Instant.now(),
                Instant.now()
        );

        PaymentEntity p2 = new PaymentEntity(
                "p2",
                id2,
                "c2",
                "cust2",
                "COMPLETED",
                BigDecimal.ZERO,
                "USD",
                "mock",
                null,
                null,
                Instant.now(),
                Instant.now()
        );

        when(paymentRepository.findByOrderIdIn(anyCollection())).thenReturn(Flux.just(p1, p2));

        webTestClient.get()
                .uri("/payments/status?orderIds={ids}", id1 + "," + id2)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(PaymentStatusDto.class)
                .hasSize(2)
                .consumeWith(res -> {
                    List<PaymentStatusDto> body = res.getResponseBody();
                    assertThat(body).isNotNull();

                    PaymentStatusDto first = body.stream().filter(d -> id1.equals(d.orderId())).findFirst().orElseThrow();
                    PaymentStatusDto second = body.stream().filter(d -> id2.equals(d.orderId())).findFirst().orElseThrow();

                    assertThat(first.failureReason()).isEqualTo("DECLINED");
                    assertThat(second.failureReason()).isNull();
                });

        verify(paymentRepository).findByOrderIdIn(anyCollection());
    }
}
