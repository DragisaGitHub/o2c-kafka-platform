package rs.master.o2c.checkout.persistence.entity;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("inbox_processed")
public class InboxProcessedEntity implements Persistable<String> {

        @Id
        @Column("message_id")
        private String messageId;

        @Column("processed_at")
        private Instant processedAt;

        @Transient
        private boolean isNew = true;

        public InboxProcessedEntity(String messageId, Instant processedAt) {
                this.messageId = messageId;
                this.processedAt = processedAt;
        }

        @Override public String getId() { return messageId; }
        @Override public boolean isNew() { return isNew; }
        public void markNotNew() { this.isNew = false; }

        public String messageId() { return messageId; }
        public Instant processedAt() { return processedAt; }
}