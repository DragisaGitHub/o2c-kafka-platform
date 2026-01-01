package rs.master.o2c.checkout.persistence.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import rs.master.o2c.checkout.persistence.entity.InboxProcessedEntity;

public interface InboxProcessedRepository extends ReactiveCrudRepository<InboxProcessedEntity, String> {
}