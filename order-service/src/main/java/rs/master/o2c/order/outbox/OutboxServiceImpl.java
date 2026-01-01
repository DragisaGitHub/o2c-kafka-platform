package rs.master.o2c.order.outbox;

import java.time.Instant;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import rs.master.o2c.order.persistence.entity.OutboxEventEntity;
import rs.master.o2c.order.persistence.repository.OutboxEventRepository;

@Service
@RequiredArgsConstructor
public class OutboxServiceImpl implements OutboxService {

    private final OutboxEventRepository repository;

    @Override
    public Mono<Integer> claim(int limit, String lockedBy) {
        return repository.claimUnpublished(limit, lockedBy);
    }

    @Override
    public Flux<OutboxEventEntity> fetchClaimed(String lockedBy) {
        return repository.findClaimed(lockedBy);
    }

    @Override
    public Mono<Void> markPublished(OutboxEventEntity event) {
        return repository
                .markPublished(event.id(), Instant.now())
                .then();
    }

    @Override
    public Mono<OutboxEventEntity> save(OutboxEventEntity event) {
        return repository.save(event);
    }
}