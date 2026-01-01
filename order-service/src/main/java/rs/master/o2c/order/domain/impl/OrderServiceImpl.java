package rs.master.o2c.order.domain.impl;

import java.time.Instant;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import rs.master.o2c.events.AggregateTypes;
import rs.master.o2c.events.CorrelationHeaders;
import rs.master.o2c.events.EventEnvelope;
import rs.master.o2c.events.order.OrderCreated;
import rs.master.o2c.events.order.OrderStatus;
import rs.master.o2c.order.api.dto.CreateOrderRequest;
import rs.master.o2c.order.domain.service.OrderService;
import rs.master.o2c.order.messaging.mapper.OrderEventMapper;
import rs.master.o2c.order.messaging.serializer.EventJsonSerializer;
import rs.master.o2c.order.outbox.OutboxService;
import rs.master.o2c.order.persistence.entity.OrderEntity;
import rs.master.o2c.order.persistence.entity.OutboxEventEntity;
import rs.master.o2c.order.persistence.repository.OrderRepository;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OutboxService outboxService;
    private final TransactionalOperator tx;
    private final OrderEventMapper mapper;
    private final EventJsonSerializer serializer;

    @Override
    @SuppressWarnings("null")
    public Mono<OrderEntity> create(CreateOrderRequest request) {
        return Mono.deferContextual(ctx -> {
            String correlationId = ctx.getOrDefault(CorrelationHeaders.X_CORRELATION_ID, null);
            if (correlationId == null || correlationId.isBlank()) {
                return Mono.<OrderEntity>error(new IllegalStateException("Missing correlationId in request context"));
            }

            String orderId = UUID.randomUUID().toString();

            OrderEntity order = new OrderEntity(
                orderId,
                String.valueOf(request.customerId()),
                OrderStatus.CREATED,
                request.totalAmount(),
                request.currency(),
                Instant.now(),
                correlationId
            );

            return tx.transactional(
                orderRepository.save(order)
                    .flatMap(saved -> {
                    EventEnvelope<OrderCreated> envelope = mapper.toOrderCreatedEnvelope(saved);

                    OutboxEventEntity outbox = new OutboxEventEntity(
                        envelope.messageId().toString(),
                        AggregateTypes.ORDER,
                        saved.id(),
                        envelope.eventType(),
                        serializer.toJson(envelope),
                        Instant.now(),
                        null
                    );

                    return outboxService.save(outbox).thenReturn(saved);
                    })
            );
        });
    }
}
