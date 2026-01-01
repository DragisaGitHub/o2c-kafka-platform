package rs.master.o2c.payment.persistence;

import org.springframework.data.r2dbc.mapping.event.AfterConvertCallback;
import org.springframework.data.relational.core.sql.SqlIdentifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import rs.master.o2c.payment.persistence.entity.PaymentEntity;

@Component
public class PaymentAfterConvertCallback implements AfterConvertCallback<PaymentEntity> {

    @Override
    public Mono<PaymentEntity> onAfterConvert(PaymentEntity entity, SqlIdentifier table) {
        entity.markNotNew();
        return Mono.just(entity);
    }
}