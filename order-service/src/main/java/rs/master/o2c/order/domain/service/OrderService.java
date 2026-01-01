package rs.master.o2c.order.domain.service;

import reactor.core.publisher.Mono;
import rs.master.o2c.order.api.dto.CreateOrderRequest;
import rs.master.o2c.order.persistence.entity.OrderEntity;

public interface OrderService {
    Mono<OrderEntity> create(CreateOrderRequest request);
}