package rs.master.o2c.checkout.persistence;

import org.springframework.data.r2dbc.mapping.event.AfterConvertCallback;
import org.springframework.data.relational.core.sql.SqlIdentifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import rs.master.o2c.checkout.persistence.entity.InboxProcessedEntity;

@Component
public class InboxProcessedAfterConvertCallback implements AfterConvertCallback<InboxProcessedEntity> {

    @Override
    public Mono<InboxProcessedEntity> onAfterConvert(InboxProcessedEntity entity, SqlIdentifier table) {
        entity.markNotNew();
        return Mono.just(entity);
    }
}