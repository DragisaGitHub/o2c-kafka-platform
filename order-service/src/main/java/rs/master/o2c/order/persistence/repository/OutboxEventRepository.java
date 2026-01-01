package rs.master.o2c.order.persistence.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import rs.master.o2c.order.persistence.entity.OutboxEventEntity;

public interface OutboxEventRepository extends ReactiveCrudRepository<OutboxEventEntity, String>, OutboxEventCustomRepository {
}