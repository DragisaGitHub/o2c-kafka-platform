package rs.master.o2c.infra.kafka;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KafkaRetryPoliciesTest {

    @Test
    void processingRetry_shouldRetryExactly8Times() {
        AtomicInteger attempts = new AtomicInteger();

        Mono<Void> op = Mono.defer(() -> {
                    attempts.incrementAndGet();
                    return Mono.error(new RuntimeException("boom"));
                })
                .retryWhen(KafkaRetryPolicies.processingRetry())
                .then();

        StepVerifier.withVirtualTime(() -> op)
                .thenAwait(Duration.ofMinutes(5))
                .expectError(RuntimeException.class)
                .verify();

        // 1 initial attempt + 8 retries
        assertEquals(9, attempts.get());
    }

    @Test
    void dlqRetry_shouldRetryExactly8Times() {
        AtomicInteger attempts = new AtomicInteger();

        Mono<Void> op = Mono.defer(() -> {
                    attempts.incrementAndGet();
                    return Mono.error(new RuntimeException("boom"));
                })
                .retryWhen(KafkaRetryPolicies.dlqRetry())
                .then();

        StepVerifier.withVirtualTime(() -> op)
                .thenAwait(Duration.ofMinutes(5))
                .expectError(RuntimeException.class)
                .verify();

        assertEquals(9, attempts.get());
    }
}
