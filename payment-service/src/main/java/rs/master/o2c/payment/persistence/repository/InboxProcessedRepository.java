package rs.master.o2c.payment.persistence.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import rs.master.o2c.payment.persistence.entity.InboxProcessedEntity;

public interface InboxProcessedRepository
        extends ReactiveCrudRepository<InboxProcessedEntity, String> {
}