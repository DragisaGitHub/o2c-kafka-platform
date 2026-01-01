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

    @Query("""
            select
                id as paymentId,
                status as status,
                failure_reason as failureReason,
                created_at as createdAt,
                updated_at as updatedAt
            from payment
            where order_id = :orderId
            """)
    Mono<PaymentTimelineRow> findTimelinePaymentByOrderId(String orderId);

    @Query("""
            select
                attempt_no as attemptNo,
                status as status,
                reason as failureReason,
                created_at as createdAt
            from payment_attempt
            where payment_id = :paymentId
            order by attempt_no asc
            """)
    Flux<PaymentAttemptRow> findAttemptsByPaymentId(String paymentId);

    @Query("""
            select
                order_id as orderId,
                status as status,
                failure_reason as failureReason
            from payment
            where order_id in (:orderIds)
            """)
    Flux<PaymentStatusRow> findStatusByOrderIdIn(Collection<String> orderIds);

    interface PaymentStatusRow {
        String orderId();
        String status();
        String failureReason();
    }

    interface PaymentTimelineRow {
        String paymentId();
        String status();
        String failureReason();
        java.time.Instant createdAt();
        java.time.Instant updatedAt();
    }

    interface PaymentAttemptRow {
        Integer attemptNo();
        String status();
        String failureReason();
        java.time.Instant createdAt();
    }
}