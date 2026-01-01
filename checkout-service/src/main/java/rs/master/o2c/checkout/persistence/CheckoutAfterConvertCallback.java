package rs.master.o2c.checkout.persistence;

import org.springframework.data.r2dbc.mapping.event.AfterConvertCallback;
import org.springframework.data.relational.core.sql.SqlIdentifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import rs.master.o2c.checkout.persistence.entity.CheckoutEntity;

@Component
public class CheckoutAfterConvertCallback implements AfterConvertCallback<CheckoutEntity> {

    @Override
    public Mono<CheckoutEntity> onAfterConvert(CheckoutEntity entity, SqlIdentifier table) {
        entity.markNotNew();
        return Mono.just(entity);
    }
}