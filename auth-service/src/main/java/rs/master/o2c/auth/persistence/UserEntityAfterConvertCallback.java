package rs.master.o2c.auth.persistence;

import org.springframework.data.r2dbc.mapping.event.AfterConvertCallback;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import rs.master.o2c.auth.persistence.entity.UserEntity;

@Component
public class UserEntityAfterConvertCallback implements AfterConvertCallback<UserEntity> {
    @Override
    public Mono<UserEntity> onAfterConvert(UserEntity entity, org.springframework.data.relational.core.sql.SqlIdentifier table) {
        entity.markNotNew();
        return Mono.just(entity);
    }
}
