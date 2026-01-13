package rs.master.o2c.infra.kafka;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;
import reactor.kafka.receiver.ReceiverRecord;
import rs.master.o2c.events.CorrelationHeaders;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DlqRecordSupportTest {

    @Test
    void enrich_shouldCopyCorrelationIdAndAddOriginHeaders() {
        RecordHeaders inHeaders = new RecordHeaders();
        inHeaders.add(CorrelationHeaders.X_CORRELATION_ID, "cid".getBytes(StandardCharsets.UTF_8));

        @SuppressWarnings("unchecked")
        ReceiverRecord<String, String> in = (ReceiverRecord<String, String>) mock(ReceiverRecord.class);
        when(in.headers()).thenReturn(inHeaders);
        when(in.topic()).thenReturn("source.topic");
        when(in.partition()).thenReturn(7);
        when(in.offset()).thenReturn(123L);

        ProducerRecord<String, String> out = new ProducerRecord<>("dlq.topic", "k", "v");

        DlqRecordSupport.enrich(out, in, new IllegalStateException("boom"));

        var cid = out.headers().lastHeader(CorrelationHeaders.X_CORRELATION_ID);
        assertNotNull(cid);
        assertEquals("cid", new String(cid.value(), StandardCharsets.UTF_8));

        assertEquals(
                "source.topic",
                new String(out.headers().lastHeader(DlqRecordSupport.HDR_ORIGINAL_TOPIC).value(), StandardCharsets.UTF_8)
        );
        assertEquals(
                "7",
                new String(out.headers().lastHeader(DlqRecordSupport.HDR_ORIGINAL_PARTITION).value(), StandardCharsets.UTF_8)
        );
        assertEquals(
                "123",
                new String(out.headers().lastHeader(DlqRecordSupport.HDR_ORIGINAL_OFFSET).value(), StandardCharsets.UTF_8)
        );
        assertEquals(
                IllegalStateException.class.getName(),
                new String(out.headers().lastHeader(DlqRecordSupport.HDR_ERROR_CLASS).value(), StandardCharsets.UTF_8)
        );
    }

    @Test
    void enrich_shouldNotAddCorrelationHeaderWhenMissing() {
        RecordHeaders inHeaders = new RecordHeaders();

        @SuppressWarnings("unchecked")
        ReceiverRecord<String, String> in = (ReceiverRecord<String, String>) mock(ReceiverRecord.class);
        when(in.headers()).thenReturn(inHeaders);
        when(in.topic()).thenReturn("source.topic");
        when(in.partition()).thenReturn(0);
        when(in.offset()).thenReturn(0L);

        ProducerRecord<String, String> out = new ProducerRecord<>("dlq.topic", "k", "v");

        DlqRecordSupport.enrich(out, in, new RuntimeException("boom"));

        assertNull(out.headers().lastHeader(CorrelationHeaders.X_CORRELATION_ID));
        assertNotNull(out.headers().lastHeader(DlqRecordSupport.HDR_ORIGINAL_TOPIC));
    }
}
