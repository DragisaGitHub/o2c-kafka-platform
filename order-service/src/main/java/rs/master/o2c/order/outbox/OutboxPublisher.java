package rs.master.o2c.order.outbox;

import java.time.Duration;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import rs.master.o2c.events.TopicNames;
import rs.master.o2c.order.persistence.entity.OutboxEventEntity;

@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxService outboxService;
    private final ReactiveKafkaProducerTemplate<String, String> producer;

    private final String instanceId = UUID.randomUUID().toString();

    @Scheduled(fixedDelay = 2000)
    public void publish() {
        outboxService
                .claim(50, instanceId)
                .flatMapMany(ignored -> outboxService.fetchClaimed(instanceId))
                .concatMap(this::publishOne)
                .retryWhen(
                        Retry.backoff(10, Duration.ofSeconds(1))
                                .maxBackoff(Duration.ofSeconds(30))
                )
                .subscribe();
    }

    private Mono<Void> publishOne(OutboxEventEntity event) {
        return producer
                .send(
                        TopicNames.ORDER_EVENTS_V1,
                        event.aggregateId(),
                        event.payload()
                )
                .then(outboxService.markPublished(event));
    }
}