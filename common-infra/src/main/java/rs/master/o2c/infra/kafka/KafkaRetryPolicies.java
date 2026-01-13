package rs.master.o2c.infra.kafka;

import reactor.util.retry.Retry;

import java.time.Duration;

public final class KafkaRetryPolicies {

    private KafkaRetryPolicies() {}

    public static Retry processingRetry() {
        return Retry
                .backoff(8, Duration.ofMillis(250))
                .maxBackoff(Duration.ofSeconds(20))
                .jitter(0.25);
    }

    public static Retry dlqRetry() {
        return Retry
                .backoff(8, Duration.ofMillis(250))
                .maxBackoff(Duration.ofSeconds(20))
                .jitter(0.25);
    }

    public static Retry streamRetry() {
        return Retry
                .backoff(Long.MAX_VALUE, Duration.ofSeconds(1))
                .maxBackoff(Duration.ofSeconds(30))
                .jitter(0.25);
    }
}
