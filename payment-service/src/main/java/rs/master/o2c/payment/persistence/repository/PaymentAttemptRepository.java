package rs.master.o2c.payment.persistence.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import rs.master.o2c.payment.persistence.entity.PaymentAttemptEntity;

public interface PaymentAttemptRepository extends ReactiveCrudRepository<PaymentAttemptEntity, Long> {
    Flux<PaymentAttemptEntity> findByPaymentIdOrderByAttemptNoAsc(String paymentId);

    Mono<PaymentAttemptEntity> findByProviderPaymentId(String providerPaymentId);

    @Query("""
        update payment_attempt
        set provider_payment_id = :providerPaymentId
        where payment_id = :paymentId and attempt_no = :attemptNo
        """)
    Mono<Integer> setProviderPaymentId(String paymentId, int attemptNo, String providerPaymentId);

    @Query("""
        update payment_attempt
        set status = :status,
            reason = :reason,
            updated_at = current_timestamp
        where id = :id
        """)
    Mono<Integer> updateStatusAndReasonById(long id, String status, String reason);

    @Query("""
        update payment_attempt
        set status = :status,
            reason = :reason,
            updated_at = current_timestamp
        where payment_id = :paymentId and attempt_no = :attemptNo
        """)
    Mono<Integer> updateStatusAndReasonByPaymentIdAndAttemptNo(String paymentId, int attemptNo, String status, String reason);
}