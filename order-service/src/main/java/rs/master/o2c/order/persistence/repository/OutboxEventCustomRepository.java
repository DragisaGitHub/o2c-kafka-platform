package rs.master.o2c.order.persistence.repository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import rs.master.o2c.order.persistence.entity.OutboxEventEntity;

import java.time.Instant;

public interface OutboxEventCustomRepository {

    Mono<Integer> claimUnpublished(int limit, String lockedBy);

    Flux<OutboxEventEntity> findClaimed(String lockedBy);

    Mono<Boolean> markPublished(String id, Instant publishedAt);
}