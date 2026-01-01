package rs.master.o2c.payment.persistence.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import rs.master.o2c.payment.persistence.entity.PaymentAttemptEntity;

public interface PaymentAttemptRepository extends ReactiveCrudRepository<PaymentAttemptEntity, Long> {
    Flux<PaymentAttemptEntity> findByPaymentIdOrderByAttemptNoAsc(String paymentId);
}