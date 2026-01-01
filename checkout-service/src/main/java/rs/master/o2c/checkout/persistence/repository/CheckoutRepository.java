package rs.master.o2c.checkout.persistence.repository;

import java.util.Collection;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import rs.master.o2c.checkout.persistence.entity.CheckoutEntity;

public interface CheckoutRepository extends ReactiveCrudRepository<CheckoutEntity, String> {

    Mono<CheckoutEntity> findByOrderId(String orderId);

    Flux<CheckoutEntity> findByOrderIdIn(Collection<String> orderIds);
}