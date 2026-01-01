package rs.master.o2c.order.persistence.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.relational.core.query.Update;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import rs.master.o2c.order.persistence.entity.OutboxEventEntity;

import java.time.Instant;

@Repository
@RequiredArgsConstructor
public class OutboxEventCustomRepositoryImpl implements OutboxEventCustomRepository {

    private final R2dbcEntityTemplate template;

    @Override
    public Mono<Integer> claimUnpublished(int limit, String lockedBy) {
        Query select = Query.query(
                        Criteria.where("published_at").isNull()
                                .and("locked_at").isNull()
                )
                .sort(Sort.by(Sort.Direction.ASC, "created_at"))
                .limit(limit);

        Update update = Update.update("locked_at", Instant.now())
                .set("locked_by", lockedBy);

        return template
                .select(select, OutboxEventEntity.class)
                .flatMap(e ->
                        template.update(
                                Query.query(Criteria.where("id").is(e.id())),
                                update,
                                OutboxEventEntity.class
                        )
                )
                .count()
                .map(Long::intValue);
    }

    @Override
    public Flux<OutboxEventEntity> findClaimed(String lockedBy) {
        Query query = Query.query(
                Criteria.where("locked_by").is(lockedBy)
                        .and("published_at").isNull()
        );

        return template.select(query, OutboxEventEntity.class);
    }

    @Override
    public Mono<Boolean> markPublished(String id, Instant publishedAt) {
        Query query = Query.query(
                Criteria.where("id").is(id)
                        .and("published_at").isNull()
        );

        Update update = Update.update("published_at", publishedAt);

        return template.update(query, update, OutboxEventEntity.class)
                .map(rows -> rows > 0);
    }
}