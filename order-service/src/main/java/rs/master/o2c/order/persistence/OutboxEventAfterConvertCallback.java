package rs.master.o2c.order.persistence;

import org.springframework.data.r2dbc.mapping.event.AfterConvertCallback;
import org.springframework.data.relational.core.sql.SqlIdentifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import rs.master.o2c.order.persistence.entity.OutboxEventEntity;

@Component
public class OutboxEventAfterConvertCallback implements AfterConvertCallback<OutboxEventEntity> {
    @Override
    public Mono<OutboxEventEntity> onAfterConvert(OutboxEventEntity entity, SqlIdentifier table) {
        entity.markNotNew();
        return Mono.just(entity);
    }
}

