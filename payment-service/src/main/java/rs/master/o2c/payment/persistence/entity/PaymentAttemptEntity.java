package rs.master.o2c.payment.persistence.entity;

import java.time.Instant;

import org.springframework.data.annotation.Id;
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

    @Column("created_at")
    private Instant createdAt;

    public PaymentAttemptEntity(
            Long id,
            String paymentId,
            Integer attemptNo,
            String status,
            String reason,
            Instant createdAt
    ) {
        this.id = id;
        this.paymentId = paymentId;
        this.attemptNo = attemptNo;
        this.status = status;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public Long id() { return id; }
    public String paymentId() { return paymentId; }
    public Integer attemptNo() { return attemptNo; }
    public String status() { return status; }
    public String reason() { return reason; }
    public Instant createdAt() { return createdAt; }
}