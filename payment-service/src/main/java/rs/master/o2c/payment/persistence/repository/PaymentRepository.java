package rs.master.o2c.payment.persistence.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;
import rs.master.o2c.payment.persistence.entity.PaymentEntity;

public interface PaymentRepository extends ReactiveCrudRepository<PaymentEntity, String> {
    Mono<PaymentEntity> findByCheckoutId(String checkoutId);
    Mono<Boolean> existsByCheckoutId(String checkoutId);
}