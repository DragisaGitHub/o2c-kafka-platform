package rs.master.o2c.order.persistence;

import org.springframework.data.r2dbc.mapping.event.AfterConvertCallback;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import rs.master.o2c.order.persistence.entity.OrderEntity;

@Component
public class OrderEntityAfterConvertCallback implements AfterConvertCallback<OrderEntity> {
    @Override
    public Mono<OrderEntity> onAfterConvert(OrderEntity entity, org.springframework.data.relational.core.sql.SqlIdentifier table) {
        entity.markNotNew();
        return Mono.just(entity);
    }
}