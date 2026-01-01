package rs.master.o2c.order.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import rs.master.o2c.events.CorrelationHeaders;
import rs.master.o2c.events.order.OrderStatus;
import rs.master.o2c.order.config.SecurityConfig;
import rs.master.o2c.order.observability.CorrelationIdWebFilter;
import rs.master.o2c.order.persistence.entity.OrderEntity;
import rs.master.o2c.order.persistence.repository.OrderRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.when;

@WebFluxTest(controllers = OrderDetailsController.class)
@Import({SecurityConfig.class, CorrelationIdWebFilter.class})
@SuppressWarnings({"null", "removal"})
class OrderDetailsControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private OrderRepository orderRepository;

    @Test
    void get_shouldReturn400_whenOrderIdInvalid() {
        webTestClient.get()
                .uri("/orders/not-a-uuid")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void get_shouldReturn404_whenOrderNotFound() {
        String orderId = UUID.randomUUID().toString();

        when(orderRepository.findById(orderId)).thenReturn(Mono.empty());

        webTestClient.get()
                .uri("/orders/{orderId}", orderId)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void get_shouldReturnOrderDetails_includingCorrelationIdFromDb() {
        String requestCorrelationId = "cid-request";
        String storedCorrelationId = "cid-stored";

        String orderId = UUID.randomUUID().toString();
        String customerId = UUID.randomUUID().toString();

        OrderEntity entity = new OrderEntity(
                orderId,
                customerId,
                OrderStatus.CREATED,
                new BigDecimal("12.34"),
                "USD",
                Instant.parse("2026-01-01T00:00:00Z"),
                storedCorrelationId
        );

        when(orderRepository.findById(orderId)).thenReturn(Mono.just(entity));

        webTestClient.get()
                .uri("/orders/{orderId}", orderId)
                .header(CorrelationHeaders.X_CORRELATION_ID, requestCorrelationId)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(CorrelationHeaders.X_CORRELATION_ID, requestCorrelationId)
                .expectBody()
                .jsonPath("$.orderId").isEqualTo(orderId)
                .jsonPath("$.customerId").isEqualTo(customerId)
                .jsonPath("$.status").isEqualTo(OrderStatus.CREATED)
                .jsonPath("$.totalAmount").isEqualTo(12.34)
                .jsonPath("$.currency").isEqualTo("USD")
                .jsonPath("$.correlationId").isEqualTo(storedCorrelationId);
    }
}
