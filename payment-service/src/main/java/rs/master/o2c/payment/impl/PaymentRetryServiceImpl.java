package rs.master.o2c.payment.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import rs.master.o2c.events.CorrelationHeaders;
import rs.master.o2c.events.EventEnvelope;
import rs.master.o2c.events.EventTypes;
import rs.master.o2c.events.ProducerNames;
import rs.master.o2c.events.TopicNames;
import rs.master.o2c.events.payment.PaymentRequested;
import rs.master.o2c.payment.api.dto.RetryPaymentRequest;
import rs.master.o2c.payment.service.PaymentRetryService;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentRetryServiceImpl implements PaymentRetryService {

    private static final String SQL_SELECT_PAYMENT_INFO = """
            select
                checkout_id as checkoutId,
                customer_id as customerId,
                total_amount as amount,
                currency as currency
            from payment
            where order_id = :orderId
            limit 1
            """;

    private static final String SQL_INSERT_RETRY = """
            insert into payment_retry_request (retry_request_id, order_id)
            values (:retryRequestId, :orderId)
            """;

    private final DatabaseClient databaseClient;
    private final ReactiveKafkaProducerTemplate<String, String> producer;
    private final ObjectMapper objectMapper;

    public PaymentRetryServiceImpl(
            DatabaseClient databaseClient,
            ReactiveKafkaProducerTemplate<String, String> producer,
            ObjectMapper objectMapper
    ) {
        this.databaseClient = databaseClient;
        this.producer = producer;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<RetryOutcome> retry(RetryPaymentRequest request, String correlationId) {
        UUID orderId = request.orderId();

        return fetchPaymentInfo(orderId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "payment not found")))
                .flatMap(info ->
                        insertIdempotencyRow(request)
                                .then(publishPaymentRequested(request, correlationId, info))
                                .thenReturn(new RetryOutcome(false, request.retryRequestId()))
                                .onErrorResume(DuplicateKeyException.class, e ->
                                        Mono.just(new RetryOutcome(true, request.retryRequestId()))
                                )
                );
    }

    private Mono<Void> insertIdempotencyRow(RetryPaymentRequest request) {
        return databaseClient
                .sql(SQL_INSERT_RETRY)
                .bind("retryRequestId", request.retryRequestId().toString())
                .bind("orderId", request.orderId().toString())
                .fetch()
                .rowsUpdated()
                .then();
    }

    private Mono<PaymentInfo> fetchPaymentInfo(UUID orderId) {
        return databaseClient
                .sql(SQL_SELECT_PAYMENT_INFO)
                .bind("orderId", orderId.toString())
                .fetch()
                .one()
                .flatMap(map -> Mono.justOrEmpty(toPaymentInfo(map)));
    }

    private static PaymentInfo toPaymentInfo(Map<String, Object> map) {
        if (map == null || map.isEmpty()) return null;

        Object checkoutId = map.get("checkoutId");
        Object customerId = map.get("customerId");
        Object amount = map.get("amount");
        Object currency = map.get("currency");

        if (!(checkoutId instanceof String c) || c.isBlank()) return null;
        if (!(customerId instanceof String cu) || cu.isBlank()) return null;
        if (!(currency instanceof String cur) || cur.isBlank()) return null;

        BigDecimal amt;
        if (amount instanceof BigDecimal bd) {
            amt = bd;
        } else if (amount instanceof Number n) {
            amt = BigDecimal.valueOf(n.doubleValue());
        } else {
            return null;
        }

        return new PaymentInfo(c.trim(), cu.trim(), amt, cur.trim());
    }

    private Mono<Void> publishPaymentRequested(RetryPaymentRequest request, String correlationId, PaymentInfo info) {
        return Mono.fromCallable(() -> toJsonPayload(request, correlationId, info))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(json -> {
                    ProducerRecord<String, String> record = new ProducerRecord<>(
                            TopicNames.PAYMENT_REQUESTS_V1,
                            request.orderId().toString(),
                            json
                    );

                    if (correlationId != null && !correlationId.isBlank()) {
                        record.headers().add(
                                CorrelationHeaders.X_CORRELATION_ID,
                                correlationId.trim().getBytes(StandardCharsets.UTF_8)
                        );
                    }

                    return producer.send(record).then();
                });
    }

    private String toJsonPayload(RetryPaymentRequest request, String correlationId, PaymentInfo info) throws JsonProcessingException {
        UUID stableMessageId = request.retryRequestId();
        UUID parsedCorrelationId = parseCorrelationIdOrNull(correlationId);

        EventEnvelope<PaymentRequested> envelope = new EventEnvelope<>(
                stableMessageId,
                parsedCorrelationId,
                stableMessageId,
                EventTypes.PAYMENT_REQUESTED,
                1,
                Instant.now(),
                ProducerNames.PAYMENT_SERVICE,
                request.orderId().toString(),
                new PaymentRequested(
                        info.checkoutId(),
                        request.orderId().toString(),
                        info.customerId(),
                        info.amount(),
                        info.currency()
                )
        );

        return objectMapper.writeValueAsString(envelope);
    }

    private static UUID parseCorrelationIdOrNull(String correlationId) {
        if (correlationId == null || correlationId.isBlank()) {
            return null;
        }

        try {
            return UUID.fromString(correlationId.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private record PaymentInfo(String checkoutId, String customerId, BigDecimal amount, String currency) {}
}
