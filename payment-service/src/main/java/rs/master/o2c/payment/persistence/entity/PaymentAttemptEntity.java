package rs.master.o2c.payment.persistence.entity;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.ReadOnlyProperty;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("payment_attempt")
public class PaymentAttemptEntity {

    @Id
    private Long id;

    @Column("payment_id")
    private String paymentId;

    @Column("attempt_no")
    private Integer attemptNo;

    private String status;

    private String reason;

    @Column("provider_payment_id")
    private String providerPaymentId;

    @Column("created_at")
    @ReadOnlyProperty
    private Instant createdAt;

    @Column("updated_at")
    @ReadOnlyProperty
    private Instant updatedAt;

    public PaymentAttemptEntity(
            Long id,
            String paymentId,
            Integer attemptNo,
            String status,
            String reason,
            String providerPaymentId,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.paymentId = paymentId;
        this.attemptNo = attemptNo;
        this.status = status;
        this.reason = reason;
        this.providerPaymentId = providerPaymentId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long id() { return id; }
    public String paymentId() { return paymentId; }
    public Integer attemptNo() { return attemptNo; }
    public String status() { return status; }
    public String reason() { return reason; }
    public String providerPaymentId() { return providerPaymentId; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}