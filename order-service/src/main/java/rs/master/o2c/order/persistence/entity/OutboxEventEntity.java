package rs.master.o2c.order.persistence.entity;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("outbox_event")
public class OutboxEventEntity implements Persistable<String> {

        @Id
        private String id;

        @Column("aggregate_type")
        private String aggregateType;

        @Column("aggregate_id")
        private String aggregateId;

        @Column("event_type")
        private String eventType;

        private String payload;

        @Column("created_at")
        private Instant createdAt;

        @Column("published_at")
        private Instant publishedAt;

        @Column("locked_at")
        private Instant lockedAt;

        @Column("locked_by")
        private String lockedBy;

        @Transient
        private boolean isNew = true;

        public OutboxEventEntity(String id, String aggregateType, String aggregateId, String eventType, String payload, Instant createdAt, Instant publishedAt) {
                this.id = id;
                this.aggregateType = aggregateType;
                this.aggregateId = aggregateId;
                this.eventType = eventType;
                this.payload = payload;
                this.createdAt = createdAt;
                this.publishedAt = publishedAt;
        }

        @Override public String getId() { return id; }
        @Override public boolean isNew() { return isNew; }
        public void markNotNew() { this.isNew = false; }

        public String id() { return id; }
        public String aggregateType() { return aggregateType; }
        public String aggregateId() { return aggregateId; }
        public String eventType() { return eventType; }
        public String payload() { return payload; }
        public Instant createdAt() { return createdAt; }
        public Instant publishedAt() { return publishedAt; }
}