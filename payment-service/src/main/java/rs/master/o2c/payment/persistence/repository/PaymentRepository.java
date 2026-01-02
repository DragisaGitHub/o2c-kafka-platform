package rs.master.o2c.payment.persistence.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import rs.master.o2c.payment.persistence.entity.PaymentEntity;

import java.util.Collection;

public interface PaymentRepository extends ReactiveCrudRepository<PaymentEntity, String> {
    Mono<PaymentEntity> findByCheckoutId(String checkoutId);
    Mono<Boolean> existsByCheckoutId(String checkoutId);

    Mono<PaymentEntity> findByOrderId(String orderId);
    Flux<PaymentEntity> findByOrderIdIn(Collection<String> orderIds);

    @Query("""
        select coalesce(max(attempt_no), 0)
        from payment_attempt
        where payment_id = :paymentId
        """)
    Mono<Integer> findMaxAttemptNo(String paymentId);

    @Query("""
        insert into payment_attempt (payment_id, attempt_no, status, reason, created_at)
        values (:paymentId, :attemptNo, :status, :reason, current_timestamp)
        """)
    Mono<Integer> insertAttempt(String paymentId, int attemptNo, String status, String reason);
}