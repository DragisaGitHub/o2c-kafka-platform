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
}