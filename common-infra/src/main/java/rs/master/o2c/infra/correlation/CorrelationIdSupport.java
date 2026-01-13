package rs.master.o2c.infra.correlation;

import org.apache.kafka.common.header.Header;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class CorrelationIdSupport {

    private CorrelationIdSupport() {}

    /**
     * Preserves the original value if present (does not trim), matching the existing WebFilter behavior.
     */
    public static String ensureOrGenerate(String correlationId) {
        if (correlationId == null || correlationId.trim().isEmpty()) {
            return UUID.randomUUID().toString();
        }
        return correlationId;
    }

    /**
     * Normalizes (trims) a present correlation id; generates a UUID if missing.
     */
    public static String normalizeOrGenerate(String correlationId) {
        if (correlationId == null || correlationId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return correlationId.trim();
    }

    public static String headerValueOrNA(Header header) {
        if (header == null || header.value() == null || header.value().length == 0) {
            return "n/a";
        }

        String value = new String(header.value(), StandardCharsets.UTF_8).trim();
        return value.isEmpty() ? "n/a" : value;
    }
}
