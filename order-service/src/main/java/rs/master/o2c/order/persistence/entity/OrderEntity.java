package rs.master.o2c.order.persistence.entity;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("orders")
public class OrderEntity implements Persistable<String> {

        @Id
        private String id;

        @Column("customer_id")
        private String customerId;

        private String status;

        @Column("total_amount")
        private BigDecimal totalAmount;

        private String currency;

        @Column("correlation_id")
        private String correlationId;

        @Column("created_at")
        private Instant createdAt;

        @Transient
        private boolean isNew = true;

        public OrderEntity(String id, String customerId, String status, BigDecimal totalAmount, String currency, Instant createdAt, String correlationId) {
                this.id = id;
                this.customerId = customerId;
                this.status = status;
                this.totalAmount = totalAmount;
                this.currency = currency;
                this.createdAt = createdAt;
                this.correlationId = correlationId;
        }

        @Override
        public String getId() { return id; }

        @Override
        public boolean isNew() { return isNew; }

        public void markNotNew() { this.isNew = false; }

        public String id() { return id; }
        public String customerId() { return customerId; }
        public String status() { return status; }
        public BigDecimal totalAmount() { return totalAmount; }
        public String currency() { return currency; }
        public Instant createdAt() { return createdAt; }
        public String correlationId() { return correlationId; }

        public void setStatus(String status) {
                this.status = status;
        }
}