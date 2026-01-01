package rs.master.o2c.events;

import java.time.Instant;
import java.util.UUID;

public record EventEnvelope<T>(
        UUID messageId,
        UUID correlationId,
        UUID causationId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        String producer,
        String key,
        T payload
) {}