package rs.master.o2c.checkout.persistence.entity;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import rs.master.o2c.events.checkout.CheckoutStatus;

@Table("checkout")
public class CheckoutEntity implements Persistable<String> {

        @Id
        private String id;

        @Column("order_id")
        private String orderId;

        @Column("customer_id")
        private String customerId;

        private String status;

        @Column("total_amount")
        private BigDecimal totalAmount;

        private String currency;

        @Column("created_at")
        private Instant createdAt;

        @Column("updated_at")
        private Instant updatedAt;

        @Transient
        private boolean isNew = true;

        public CheckoutEntity(
                String id,
                String orderId,
                String customerId,
                String status,
                BigDecimal totalAmount,
                String currency,
                Instant createdAt
        ) {
                this.id = id;
                this.orderId = orderId;
                this.customerId = customerId;
                this.status = status;
                this.totalAmount = totalAmount;
                this.currency = currency;
                this.createdAt = createdAt;
        }

        @Override
        public String getId() {
                return id;
        }

        @Override
        public boolean isNew() {
                return isNew;
        }

        public void markNotNew() {
                this.isNew = false;
        }

        public String id() {
                return id;
        }

        public String orderId() {
                return orderId;
        }

        public String customerId() {
                return customerId;
        }

        public String status() {
                return status;
        }

        public BigDecimal totalAmount() {
                return totalAmount;
        }

        public String currency() {
                return currency;
        }

        public Instant createdAt() {
                return createdAt;
        }

        public Instant updatedAt() {
                return updatedAt;
        }

        public void markFailed() {
                this.status = CheckoutStatus.FAILED;
                this.updatedAt = Instant.now();
        }

        public void markPending() {
                this.status = CheckoutStatus.PENDING;
        }

        public void markCompleted() {
                this.status = CheckoutStatus.COMPLETED;
                this.updatedAt = Instant.now();
        }

}