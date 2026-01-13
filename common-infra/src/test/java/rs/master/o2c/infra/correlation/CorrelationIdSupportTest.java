package rs.master.o2c.infra.correlation;

import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CorrelationIdSupportTest {

    @Test
    void ensureOrGenerate_shouldPreserveOriginalIfPresent() {
        String original = "  abc  ";
        assertSame(original, CorrelationIdSupport.ensureOrGenerate(original));
    }

    @Test
    void ensureOrGenerate_shouldGenerateUuidWhenMissingOrBlank() {
        assertDoesNotThrow(() -> UUID.fromString(CorrelationIdSupport.ensureOrGenerate(null)));
        assertDoesNotThrow(() -> UUID.fromString(CorrelationIdSupport.ensureOrGenerate("")));
        assertDoesNotThrow(() -> UUID.fromString(CorrelationIdSupport.ensureOrGenerate("   ")));
    }

    @Test
    void normalizeOrGenerate_shouldTrimWhenPresent() {
        assertEquals("abc", CorrelationIdSupport.normalizeOrGenerate("  abc "));
    }

    @Test
    void normalizeOrGenerate_shouldGenerateUuidWhenMissingOrBlank() {
        assertDoesNotThrow(() -> UUID.fromString(CorrelationIdSupport.normalizeOrGenerate(null)));
        assertDoesNotThrow(() -> UUID.fromString(CorrelationIdSupport.normalizeOrGenerate("")));
        assertDoesNotThrow(() -> UUID.fromString(CorrelationIdSupport.normalizeOrGenerate("\t\n")));
    }

    @Test
    void headerValueOrNA_shouldReturnNAForNullOrEmpty() {
        assertEquals("n/a", CorrelationIdSupport.headerValueOrNA(null));
        assertEquals("n/a", CorrelationIdSupport.headerValueOrNA(new RecordHeader("x", new byte[0])));
    }

    @Test
    void headerValueOrNA_shouldTrimAndReturnValue() {
        var h = new RecordHeader("x", "  cid  ".getBytes(StandardCharsets.UTF_8));
        assertEquals("cid", CorrelationIdSupport.headerValueOrNA(h));
    }
}
