package rs.master.o2c.provider.scheduling;

import java.time.Duration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import rs.master.o2c.events.CorrelationHeaders;
import rs.master.o2c.provider.config.ProviderProperties;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProviderCallbackProcessor {

    private final ProviderProperties properties;
    private final ProviderCallbackQueue queue;
    private final WebClient webhookWebClient;

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        // Allowed subscribe(): app bootstrap/infra.
        queue.flux()
                .flatMap(this::processTask)
                .onErrorContinue((ex, v) -> log.warn("provider callback processing error", ex))
                .subscribe();
    }

    private Mono<Void> processTask(ProviderCallbackTask task) {
        long delayMs = Math.max(0, properties.callbackDelayMs());

        ProviderWebhookRequest body = new ProviderWebhookRequest(
                task.providerPaymentId().toString(),
                task.status(),
                task.failureReason()
        );

        return Mono.delay(Duration.ofMillis(delayMs))
                .then(
                        webhookWebClient
                                .post()
                                .uri(task.webhookUrl())
                                .contentType(MediaType.APPLICATION_JSON)
                                .header(CorrelationHeaders.X_CORRELATION_ID, task.correlationId())
                                .bodyValue(body)
                                .retrieve()
                                .bodyToMono(Void.class)
                )
                .doOnSuccess(v -> log.info(
                        "provider webhook sent providerPaymentId={} status={} correlationId={} url={}",
                        task.providerPaymentId(),
                        task.status(),
                        task.correlationId(),
                        task.webhookUrl()
                ))
                .doOnError(ex -> log.warn(
                        "provider webhook failed providerPaymentId={} status={} correlationId={} url={}",
                        task.providerPaymentId(),
                        task.status(),
                        task.correlationId(),
                        task.webhookUrl(),
                        ex
                ))
                .onErrorResume(ex -> Mono.empty())
                .then();
    }

    public record ProviderWebhookRequest(String providerPaymentId, String status, String failureReason) {
    }
}
