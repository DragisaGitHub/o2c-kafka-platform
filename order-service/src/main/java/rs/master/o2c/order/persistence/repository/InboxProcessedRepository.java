package rs.master.o2c.order.persistence.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import rs.master.o2c.order.persistence.entity.InboxProcessedEntity;

public interface InboxProcessedRepository extends ReactiveCrudRepository<InboxProcessedEntity, String> {
}