package rs.master.o2c.payment.persistence.entity;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.domain.Persistable;

@Table("payment")
public class PaymentEntity implements Persistable<String> {

    @Id
    private String id;

    @Column("order_id")
    private String orderId;

    @Column("checkout_id")
    private String checkoutId;

    @Column("customer_id")
    private String customerId;

    private String status;

    @Column("total_amount")
    private BigDecimal totalAmount;

    private String currency;

    private String provider;

    @Column("provider_payment_id")
    private String providerPaymentId;

    @Column("failure_reason")
    private String failureReason;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;

    @Transient
    private boolean isNew = true;

    public PaymentEntity(
            String id,
            String orderId,
            String checkoutId,
            String customerId,
            String status,
            BigDecimal totalAmount,
            String currency,
            String provider,
            String providerPaymentId,
            String failureReason,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.orderId = orderId;
        this.checkoutId = checkoutId;
        this.customerId = customerId;
        this.status = status;
        this.totalAmount = totalAmount;
        this.currency = currency;
        this.provider = provider;
        this.providerPaymentId = providerPaymentId;
        this.failureReason = failureReason;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    @Override
    public String getId() { return id; }

    @Override
    public boolean isNew() { return isNew; }

    public void markNotNew() { this.isNew = false; }

    public void markFailed(String reason) {
        this.status = "FAILED";
        this.failureReason = reason;
        this.updatedAt = Instant.now();
    }

    public void markSucceeded(String providerPaymentId) {
        this.status = "SUCCEEDED";
        this.providerPaymentId = providerPaymentId;
        this.failureReason = null;
        this.updatedAt = Instant.now();
    }

    public String id() { return id; }
    public String orderId() { return orderId; }
    public String checkoutId() { return checkoutId; }
    public String customerId() { return customerId; }
    public String status() { return status; }
    public BigDecimal totalAmount() { return totalAmount; }
    public String currency() { return currency; }
    public String provider() { return provider; }
    public String providerPaymentId() { return providerPaymentId; }
    public String failureReason() { return failureReason; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}