package rs.master.o2c.checkout.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import rs.master.o2c.checkout.api.dto.CheckoutStatusDto;
import rs.master.o2c.checkout.config.SecurityConfig;
import rs.master.o2c.checkout.observability.CorrelationIdWebFilter;
import rs.master.o2c.checkout.persistence.entity.CheckoutEntity;
import rs.master.o2c.checkout.persistence.repository.CheckoutRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser;

@WebFluxTest(controllers = CheckoutStatusController.class)
@Import({SecurityConfig.class, CorrelationIdWebFilter.class})
@SuppressWarnings({"null", "removal"})
class CheckoutStatusControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private CheckoutRepository checkoutRepository;

    @Test
    void status_shouldReturn400_whenOrderIdsMissing() {
        webTestClient.mutateWith(mockUser())
                .get()
                .uri("/checkouts/status")
                .exchange()
                .expectStatus().isBadRequest();

        verifyNoInteractions(checkoutRepository);
    }

    @Test
    void status_shouldReturn400_whenOrderIdsEmpty() {
        webTestClient.mutateWith(mockUser())
                .get()
                .uri("/checkouts/status?orderIds=")
                .exchange()
                .expectStatus().isBadRequest();

        verifyNoInteractions(checkoutRepository);
    }

    @Test
    void status_shouldReturn400_whenOrderIdsContainsInvalidUuid() {
        webTestClient.mutateWith(mockUser())
                .get()
                .uri("/checkouts/status?orderIds=not-a-uuid")
                .exchange()
                .expectStatus().isBadRequest();

        verifyNoInteractions(checkoutRepository);
    }

    @Test
    void status_shouldReturn200_andStatusesForIds_onHappyPath() {
        String id1 = UUID.randomUUID().toString();
        String id2 = UUID.randomUUID().toString();

        CheckoutEntity e1 = new CheckoutEntity(
                "c-1",
                id1,
                UUID.randomUUID().toString(),
                "PENDING",
                new BigDecimal("10.00"),
                "USD",
                Instant.parse("2026-01-01T00:00:00Z")
        );

        CheckoutEntity e2 = new CheckoutEntity(
                "c-2",
                id2,
                UUID.randomUUID().toString(),
                "COMPLETED",
                new BigDecimal("10.00"),
                "USD",
                Instant.parse("2026-01-01T00:00:00Z")
        );

        when(checkoutRepository.findByOrderIdIn(anyCollection())).thenReturn(Flux.just(e1, e2));

        webTestClient.mutateWith(mockUser())
                .get()
                .uri("/checkouts/status?orderIds={ids}", id1 + "," + id2)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(CheckoutStatusDto.class)
                .hasSize(2)
                .consumeWith(res -> {
                    List<CheckoutStatusDto> body = res.getResponseBody();
                    assertThat(body).isNotNull();
                    assertThat(body).extracting(CheckoutStatusDto::orderId).contains(id1, id2);
                });

        verify(checkoutRepository).findByOrderIdIn(anyCollection());
    }
}
