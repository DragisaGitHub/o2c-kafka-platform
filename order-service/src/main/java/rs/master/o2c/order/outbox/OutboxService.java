package rs.master.o2c.order.outbox;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import rs.master.o2c.order.persistence.entity.OutboxEventEntity;

public interface OutboxService {

    Mono<Integer> claim(int limit, String lockedBy);

    Flux<OutboxEventEntity> fetchClaimed(String lockedBy);

    Mono<Void> markPublished(OutboxEventEntity event);

    Mono<OutboxEventEntity> save(OutboxEventEntity event);
}