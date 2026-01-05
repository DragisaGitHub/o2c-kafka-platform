package rs.master.o2c.payment.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.core.publisher.Mono;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.MediaType;
import rs.master.o2c.events.CorrelationHeaders;
import rs.master.o2c.events.EventEnvelope;
import rs.master.o2c.events.EventTypes;
import rs.master.o2c.events.ProducerNames;
import rs.master.o2c.events.TopicNames;
import rs.master.o2c.events.payment.PaymentRequested;
import rs.master.o2c.events.payment.PaymentStatus;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.kafka.bootstrap-servers=localhost:9092",
        "spring.kafka.consumer.group-id=payment-service-it",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.consumer.properties.enable.auto.commit=false"
    }
)
@ActiveProfiles("local")
class PaymentFlowIT {

    private static volatile DisposableServer PROVIDER_SERVER;
    private static volatile int PROVIDER_PORT;
    private static volatile String PAYMENT_WEBHOOK_URL;

    @LocalServerPort
    int paymentServicePort;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        ensureProviderServerRunning();

        registry.add("payment.provider.enabled", () -> true);
        registry.add("payment.provider.base-url", () -> "http://localhost:" + PROVIDER_PORT);
    }

    @Autowired
    DatabaseClient databaseClient;

    private static final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeAll
    static void createTopics() {
        Map<String, Object> adminProps = Map.of(
            AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092"
        );

        try (AdminClient admin = AdminClient.create(adminProps)) {
            List<NewTopic> topics = List.of(
                    new NewTopic(TopicNames.PAYMENT_REQUESTS_V1, 1, (short) 1),
                    new NewTopic(TopicNames.PAYMENT_EVENTS_V1, 1, (short) 1),
                    new NewTopic(TopicNames.PAYMENT_EVENTS_DLQ_V1, 1, (short) 1)
            );

            try {
                admin.createTopics(topics).all().get();
            } catch (Exception e) {
                if (!(e.getCause() instanceof TopicExistsException)) {
                    throw new RuntimeException("Failed to create Kafka topics", e);
                }
            }
        }
    }

    @AfterAll
    static void stopProvider() {
        if (PROVIDER_SERVER != null) {
            PROVIDER_SERVER.disposeNow();
        }
    }

    @Test
    void consumesPaymentRequested_andPersistsPaymentAndAttempts() throws Exception {
        PAYMENT_WEBHOOK_URL = "http://localhost:" + paymentServicePort + "/webhooks/provider/payments";

        // 1) Successful payment request
        String checkoutId1 = UUID.randomUUID().toString();
        String orderId1 = UUID.randomUUID().toString();
        String customerId1 = UUID.randomUUID().toString();

        UUID correlationId1 = UUID.randomUUID();
        publishPaymentRequested(correlationId1, checkoutId1, orderId1, customerId1, new BigDecimal("123.45"), "EUR");

        awaitLatestAttemptHasProviderPaymentId(checkoutId1);
        awaitPaymentAndLatestAttempt(checkoutId1, PaymentStatus.SUCCEEDED, PaymentStatus.SUCCEEDED, null, true);

        // 2) Forced FAIL payment request
        String checkoutId2 = UUID.randomUUID().toString();
        String orderId2 = UUID.randomUUID().toString();
        String customerId2 = UUID.randomUUID().toString();

        UUID correlationId2 = UUID.randomUUID();
        publishPaymentRequested(correlationId2, checkoutId2, orderId2, customerId2, new BigDecimal("50.00"), "FAIL");

        awaitLatestAttemptHasProviderPaymentId(checkoutId2);
        awaitPaymentAndLatestAttempt(checkoutId2, PaymentStatus.FAILED, PaymentStatus.FAILED, "Forced FAIL for testing", true);
    }

    private void awaitLatestAttemptHasProviderPaymentId(String checkoutId) {
        Duration timeout = Duration.ofSeconds(25);
        Duration pollInterval = Duration.ofMillis(250);
        Instant deadline = Instant.now().plus(timeout);

        String lastAttemptStatus = null;
        String lastAttemptReason = null;
        String lastProviderPaymentId = null;

        while (Instant.now().isBefore(deadline)) {
            Map<String, Object> paymentRow = databaseClient
                    .sql("select id from payment where checkout_id = :checkoutId")
                    .bind("checkoutId", checkoutId)
                    .fetch()
                    .one()
                    .block();

            if (paymentRow == null) {
                sleep(pollInterval);
                continue;
            }

            String paymentId = String.valueOf(paymentRow.get("id"));
                Map<String, Object> attemptRow = databaseClient
                    .sql("select status, provider_payment_id from payment_attempt where payment_id = :paymentId order by attempt_no desc limit 1")
                    .bind("paymentId", paymentId)
                    .fetch()
                    .one()
                    .block();

            if (attemptRow == null) {
                sleep(pollInterval);
                continue;
            }

            // Sanity check: payment.created_at and attempt.created_at should be close (avoid CET/UTC drift).
            Integer driftSeconds = databaseClient
                    .sql("""
                        select abs(timestampdiff(second, p.created_at, a.created_at)) as drift_seconds
                        from payment p
                        join payment_attempt a on a.payment_id = p.id
                        where p.checkout_id = :checkoutId
                        order by a.attempt_no desc
                        limit 1
                        """)
                    .bind("checkoutId", checkoutId)
                    .map((row, meta) -> row.get("drift_seconds", Integer.class))
                    .one()
                    .block();

            if (driftSeconds != null) {
                assertTrue(
                        driftSeconds <= 5,
                        "created_at drift too large (expected <=5s, got " + driftSeconds + "s) for checkoutId=" + checkoutId
                );
            }

            String attemptStatus = String.valueOf(attemptRow.get("status"));
            Object providerPaymentIdObj = attemptRow.get("provider_payment_id");
            lastAttemptStatus = attemptStatus;
            lastProviderPaymentId = providerPaymentIdObj == null ? null : String.valueOf(providerPaymentIdObj);

            if (providerPaymentIdObj != null && !String.valueOf(providerPaymentIdObj).isBlank()) {
                return;
            }

            if (PaymentStatus.SUCCEEDED.equals(attemptStatus) || PaymentStatus.FAILED.equals(attemptStatus)) {
                Map<String, Object> reasonRow = databaseClient
                        .sql("select reason from payment_attempt where payment_id = :paymentId order by attempt_no desc limit 1")
                        .bind("paymentId", paymentId)
                        .fetch()
                        .one()
                        .block();
                lastAttemptReason = reasonRow == null ? null : String.valueOf(reasonRow.get("reason"));

                throw new AssertionError(
                        "Attempt reached terminal status without provider_payment_id; provider flow likely disabled or provider call failed. "
                                + "checkoutId=" + checkoutId
                                + " status=" + lastAttemptStatus
                                + " reason=" + lastAttemptReason
                );
            }

            sleep(pollInterval);
        }

        throw new AssertionError(
            "Timed out waiting for provider_payment_id for checkoutId=" + checkoutId
                + " lastStatus=" + lastAttemptStatus
                + " lastProviderPaymentId=" + lastProviderPaymentId
                + " lastReason=" + lastAttemptReason
        );
    }

    private static void publishPaymentRequested(
            UUID correlationId,
            String checkoutId,
            String orderId,
            String customerId,
            BigDecimal amount,
            String currency
    ) throws Exception {
        UUID messageId = UUID.randomUUID();

        EventEnvelope<PaymentRequested> envelope = new EventEnvelope<>(
                messageId,
                correlationId,
                null,
                EventTypes.PAYMENT_REQUESTED,
                1,
                Instant.now(),
                ProducerNames.CHECKOUT_SERVICE,
                orderId,
                new PaymentRequested(
                        checkoutId,
                        orderId,
                        customerId,
                        amount,
                        currency
                )
        );

        String json = objectMapper.writeValueAsString(envelope);

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            ProducerRecord<String, String> record = new ProducerRecord<>(TopicNames.PAYMENT_REQUESTS_V1, orderId, json);
            record.headers().add(CorrelationHeaders.X_CORRELATION_ID, correlationId.toString().getBytes(StandardCharsets.UTF_8));
            producer.send(record).get();
            producer.flush();
        }
    }

    private void awaitPaymentAndLatestAttempt(
            String checkoutId,
            String expectedPaymentStatus,
            String expectedAttemptStatus,
            String expectedAttemptReason
    ) {
        awaitPaymentAndLatestAttempt(checkoutId, expectedPaymentStatus, expectedAttemptStatus, expectedAttemptReason, false);
        }

        private void awaitPaymentAndLatestAttempt(
            String checkoutId,
            String expectedPaymentStatus,
            String expectedAttemptStatus,
            String expectedAttemptReason,
            boolean requireProviderPaymentId
        ) {
        Duration timeout = Duration.ofSeconds(25);
        Duration pollInterval = Duration.ofMillis(250);
        Instant deadline = Instant.now().plus(timeout);

        while (Instant.now().isBefore(deadline)) {
            Map<String, Object> paymentRow = databaseClient
                    .sql("select id, status, failure_reason from payment where checkout_id = :checkoutId")
                    .bind("checkoutId", checkoutId)
                    .fetch()
                    .one()
                    .block();

            if (paymentRow == null) {
                sleep(pollInterval);
                continue;
            }

            String paymentId = String.valueOf(paymentRow.get("id"));
            String paymentStatus = String.valueOf(paymentRow.get("status"));

            Map<String, Object> attemptRow = databaseClient
                    .sql("select attempt_no, status, reason, provider_payment_id from payment_attempt where payment_id = :paymentId order by attempt_no desc limit 1")
                    .bind("paymentId", paymentId)
                    .fetch()
                    .one()
                    .block();

            if (attemptRow == null) {
                sleep(pollInterval);
                continue;
            }

            String attemptStatus = String.valueOf(attemptRow.get("status"));
            Object reasonObj = attemptRow.get("reason");
            String attemptReason = reasonObj == null ? null : String.valueOf(reasonObj);

            Object providerPaymentIdObj = attemptRow.get("provider_payment_id");
            boolean hasProviderPaymentId = providerPaymentIdObj != null && !String.valueOf(providerPaymentIdObj).isBlank();

            if (expectedPaymentStatus.equals(paymentStatus) && expectedAttemptStatus.equals(attemptStatus)) {
                if (requireProviderPaymentId && !hasProviderPaymentId) {
                    sleep(pollInterval);
                    continue;
                }

                if (expectedAttemptReason == null) {
                    // Success path: reason should typically be null
                    assertEquals(expectedPaymentStatus, paymentStatus);
                    assertEquals(expectedAttemptStatus, attemptStatus);
                    return;
                }

                if (expectedAttemptReason.equals(attemptReason)) {
                    assertEquals(expectedPaymentStatus, paymentStatus);
                    assertEquals(expectedAttemptStatus, attemptStatus);
                    assertNotNull(attemptReason);
                    assertEquals(expectedAttemptReason, attemptReason);
                    return;
                }
            }

            sleep(pollInterval);
        }

        throw new AssertionError("Timed out waiting for payment + attempt rows for checkoutId=" + checkoutId);
    }

    private static void ensureProviderServerRunning() {
        if (PROVIDER_SERVER != null) {
            return;
        }

        PROVIDER_SERVER = HttpServer.create()
                .host("localhost")
                .port(0)
                .route(routes -> routes.post("/provider/payments", (req, res) ->
                        req.receive().aggregate().asString().flatMap(body -> {
                            try {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> map = objectMapper.readValue(body, Map.class);
                                String currency = String.valueOf(map.get("currency"));

                                String correlationId = req.requestHeaders().get(CorrelationHeaders.X_CORRELATION_ID);
                                if (correlationId == null || correlationId.isBlank()) {
                                    correlationId = UUID.randomUUID().toString();
                                }

                                String providerPaymentId = UUID.randomUUID().toString();
                                boolean fail = "FAIL".equalsIgnoreCase(currency);
                                String outcome = fail ? PaymentStatus.FAILED : PaymentStatus.SUCCEEDED;
                                String reason = fail ? "Forced FAIL for testing" : null;

                                // Fire-and-forget webhook callback.
                                String webhookUrl = PAYMENT_WEBHOOK_URL;
                                if (webhookUrl != null && !webhookUrl.isBlank()) {
                                    Map<String, Object> webhookBody = new java.util.HashMap<>();
                                    webhookBody.put("providerPaymentId", providerPaymentId);
                                    webhookBody.put("status", outcome);
                                    if (reason != null) {
                                    webhookBody.put("failureReason", reason);
                                    }

                                    WebClient.create()
                                            .post()
                                            .uri(webhookUrl)
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .header(CorrelationHeaders.X_CORRELATION_ID, correlationId)
                                        .bodyValue(webhookBody)
                                            .retrieve()
                                            .bodyToMono(Void.class)
                                        .delaySubscription(Duration.ofMillis(350))
                                            .onErrorResume(ex -> Mono.empty())
                                            .subscribe();
                                }

                                String json = objectMapper.writeValueAsString(Map.of(
                                        "providerPaymentId", providerPaymentId,
                                        "status", "ACCEPTED"
                                ));

                                return res.status(202)
                                    .header("Content-Type", "application/json")
                                    .sendString(Mono.just(json))
                                    .then();
                            } catch (Exception e) {
                                return res.status(500).send().then();
                            }
                        })
                ))
                .bindNow();

        PROVIDER_PORT = PROVIDER_SERVER.port();
    }

    private static void sleep(Duration d) {
        try {
            Thread.sleep(d.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
