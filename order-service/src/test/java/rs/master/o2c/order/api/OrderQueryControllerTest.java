package rs.master.o2c.order.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import rs.master.o2c.order.api.dto.OrderSummaryDto;
import rs.master.o2c.order.config.SecurityConfig;
import rs.master.o2c.order.observability.CorrelationIdWebFilter;
import rs.master.o2c.order.persistence.entity.OrderEntity;
import rs.master.o2c.order.persistence.repository.OrderRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@WebFluxTest(controllers = OrderQueryController.class)
@Import({SecurityConfig.class, CorrelationIdWebFilter.class})
@SuppressWarnings({"null", "removal"})
class OrderQueryControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private OrderRepository orderRepository;

    @Test
    void list_shouldReturn400_whenLimitIsZero() {
        webTestClient.get()
                .uri("/orders?limit=0")
                .exchange()
                .expectStatus().isBadRequest();

        verifyNoInteractions(orderRepository);
    }

    @Test
    void list_shouldReturn400_whenLimitIsGreaterThan200() {
        webTestClient.get()
                .uri("/orders?limit=201")
                .exchange()
                .expectStatus().isBadRequest();

        verifyNoInteractions(orderRepository);
    }

    @Test
    void list_shouldReturn400_whenCursorIsInvalid() {
        webTestClient.get()
                .uri("/orders?cursor=not-an-instant")
                .exchange()
                .expectStatus().isBadRequest();

        verifyNoInteractions(orderRepository);
    }

    @Test
    void list_shouldReturn400_whenCustomerIdIsInvalidUuid() {
        webTestClient.get()
                .uri("/orders?customerId=not-a-uuid")
                .exchange()
                .expectStatus().isBadRequest();

        verifyNoInteractions(orderRepository);
    }

    @Test
    void list_shouldReturn400_whenFromDateIsInvalid() {
        webTestClient.get()
                .uri("/orders?fromDate=2026-13-01")
                .exchange()
                .expectStatus().isBadRequest();

        verifyNoInteractions(orderRepository);
    }

    @Test
    void list_shouldReturn400_whenToDateIsInvalid() {
        webTestClient.get()
                .uri("/orders?toDate=2026-02-30")
                .exchange()
                .expectStatus().isBadRequest();

        verifyNoInteractions(orderRepository);
    }

    @Test
    void list_shouldDefaultLimitTo50_whenLimitMissing() {
        List<OrderEntity> many = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            many.add(new OrderEntity(
                    UUID.randomUUID().toString(),
                    UUID.randomUUID().toString(),
                    "CREATED",
                    new BigDecimal("10.00"),
                    "USD",
                    Instant.parse("2026-01-01T00:00:00Z").plusSeconds(i),
                    null
            ));
        }

        when(orderRepository.findAll()).thenReturn(Flux.fromIterable(many));

        webTestClient.get()
                .uri("/orders")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(50);

        verify(orderRepository).findAll();
        verify(orderRepository, never()).findByCustomerId(anyString());
    }

    @Test
    void list_shouldAllowLimit200Boundary() {
        when(orderRepository.findAll()).thenReturn(Flux.empty());

        webTestClient.get()
                .uri("/orders?limit=200")
                .exchange()
                .expectStatus().isOk();

        verify(orderRepository).findAll();
    }

    @Test
    void list_shouldReturn200_andListOfOrderSummaryDto_onHappyPath() {
        String customerId = UUID.randomUUID().toString();

        OrderEntity o1 = new OrderEntity(
                UUID.randomUUID().toString(),
                customerId,
                "CREATED",
                new BigDecimal("12.34"),
                "USD",
                Instant.parse("2026-01-03T10:00:00Z"),
                null
        );

        OrderEntity o2 = new OrderEntity(
                UUID.randomUUID().toString(),
                customerId,
                "CREATED",
                new BigDecimal("45.67"),
                "USD",
                Instant.parse("2026-01-02T10:00:00Z"),
                null
        );

        when(orderRepository.findByCustomerId(customerId)).thenReturn(Flux.just(o1, o2));

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/orders")
                        .queryParam("customerId", customerId)
                        .queryParam("fromDate", "2026-01-01")
                        .queryParam("toDate", "2026-01-03")
                        .queryParam("cursor", "2026-01-04T00:00:00Z")
                        .queryParam("limit", "2")
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(OrderSummaryDto.class)
                .hasSize(2)
                .consumeWith(res -> {
                    List<OrderSummaryDto> body = res.getResponseBody();
                    assertThat(body).isNotNull();
                    assertThat(body.get(0).orderId()).isNotBlank();
                    assertThat(body.get(0).customerId()).isEqualTo(customerId);
                });

        verify(orderRepository).findByCustomerId(customerId);
        verify(orderRepository, never()).findAll();
    }
}
