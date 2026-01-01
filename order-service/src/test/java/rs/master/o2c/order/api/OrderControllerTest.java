package rs.master.o2c.order.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import rs.master.o2c.events.CorrelationHeaders;
import rs.master.o2c.events.order.OrderStatus;
import rs.master.o2c.order.api.dto.CreateOrderRequest;
import rs.master.o2c.order.config.SecurityConfig;
import rs.master.o2c.order.domain.service.OrderService;
import rs.master.o2c.order.observability.CorrelationIdWebFilter;
import rs.master.o2c.order.persistence.entity.OrderEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = OrderController.class)
@Import({SecurityConfig.class, CorrelationIdWebFilter.class})
@SuppressWarnings({"null", "removal"})
class OrderControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private OrderService orderService;

    @Test
    void create_shouldReturn400_whenRequestInvalid() {
        webTestClient.post()
                .uri("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new HashMap<String, Object>())
                .exchange()
                .expectStatus().isBadRequest();

        verifyNoInteractions(orderService);
    }

    @Test
    void create_shouldReturnOrderIdStatusAndCorrelationId() {
        String expectedCorrelationId = "cid-123";
        String expectedOrderId = UUID.randomUUID().toString();

        when(orderService.create(any(CreateOrderRequest.class))).thenAnswer(inv -> {
            CreateOrderRequest req = inv.getArgument(0);
            return Mono.deferContextual(ctx -> {
                String correlationId = ctx.get(CorrelationHeaders.X_CORRELATION_ID);
                OrderEntity entity = new OrderEntity(
                        expectedOrderId,
                        String.valueOf(req.customerId()),
                        OrderStatus.CREATED,
                        req.totalAmount(),
                        req.currency(),
                        Instant.now(),
                        correlationId
                );
                return Mono.just(entity);
            });
        });

        CreateOrderRequest request = new CreateOrderRequest(
                UUID.randomUUID(),
                new BigDecimal("10.00"),
                "USD"
        );

        webTestClient.post()
                .uri("/orders")
                .header(CorrelationHeaders.X_CORRELATION_ID, expectedCorrelationId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(CorrelationHeaders.X_CORRELATION_ID, expectedCorrelationId)
                .expectBody()
                .jsonPath("$.orderId").isEqualTo(expectedOrderId)
                .jsonPath("$.status").isEqualTo(OrderStatus.CREATED)
                .jsonPath("$.correlationId").isEqualTo(expectedCorrelationId);

        verify(orderService).create(any(CreateOrderRequest.class));
    }
}
