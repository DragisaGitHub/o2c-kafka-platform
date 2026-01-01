package rs.master.o2c.order.persistence.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import rs.master.o2c.order.persistence.entity.OrderEntity;

public interface OrderRepository extends ReactiveCrudRepository<OrderEntity, String> {
}