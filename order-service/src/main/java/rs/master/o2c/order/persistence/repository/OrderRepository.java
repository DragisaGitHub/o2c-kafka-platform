package rs.master.o2c.order.persistence.repository;

import reactor.core.publisher.Flux;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import rs.master.o2c.order.persistence.entity.OrderEntity;

public interface OrderRepository extends ReactiveCrudRepository<OrderEntity, String> {

	Flux<OrderEntity> findByCustomerId(String customerId);
}