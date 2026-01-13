package rs.master.o2c.infra.kafka;

import org.apache.kafka.clients.producer.ProducerRecord;
import reactor.kafka.receiver.ReceiverRecord;
import rs.master.o2c.events.CorrelationHeaders;

import java.nio.charset.StandardCharsets;

public final class DlqRecordSupport {

    public static final String HDR_ORIGINAL_TOPIC = "x-original-topic";
    public static final String HDR_ORIGINAL_PARTITION = "x-original-partition";
    public static final String HDR_ORIGINAL_OFFSET = "x-original-offset";
    public static final String HDR_ERROR_CLASS = "x-error-class";

    private DlqRecordSupport() {}

    /**
     * Enriches a DLQ record with correlation id (if present) and origin/error metadata.
     *
     * Behavior intentionally matches the per-service implementations.
     */
    public static <K, V> void enrich(ProducerRecord<K, V> out, ReceiverRecord<K, V> in, Throwable cause) {
        var correlationHeader = in.headers().lastHeader(CorrelationHeaders.X_CORRELATION_ID);
        if (correlationHeader != null && correlationHeader.value() != null && correlationHeader.value().length > 0) {
            out.headers().add(CorrelationHeaders.X_CORRELATION_ID, correlationHeader.value());
        }

        out.headers().add(HDR_ORIGINAL_TOPIC, in.topic().getBytes(StandardCharsets.UTF_8));
        out.headers().add(HDR_ORIGINAL_PARTITION, String.valueOf(in.partition()).getBytes(StandardCharsets.UTF_8));
        out.headers().add(HDR_ORIGINAL_OFFSET, String.valueOf(in.offset()).getBytes(StandardCharsets.UTF_8));
        out.headers().add(HDR_ERROR_CLASS, cause.getClass().getName().getBytes(StandardCharsets.UTF_8));
    }
}
