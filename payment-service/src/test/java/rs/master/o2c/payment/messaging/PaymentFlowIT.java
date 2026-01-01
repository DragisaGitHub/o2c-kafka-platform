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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
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

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.kafka.consumer.group-id=payment-service-it",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.consumer.properties.enable.auto.commit=false"
    }
)
@Testcontainers
class PaymentFlowIT {

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.1")
    );

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(
            DockerImageName.parse("mysql:8.0.36")
    )
            .withDatabaseName("payment_db")
            .withUsername("root")
            .withPassword("root");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);

        registry.add("spring.r2dbc.url", () ->
                "r2dbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306) + "/" + MYSQL.getDatabaseName()
        );
        registry.add("spring.r2dbc.username", MYSQL::getUsername);
        registry.add("spring.r2dbc.password", MYSQL::getPassword);

        registry.add("spring.flyway.url", MYSQL::getJdbcUrl);
        registry.add("spring.flyway.user", MYSQL::getUsername);
        registry.add("spring.flyway.password", MYSQL::getPassword);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

    @Autowired
    DatabaseClient databaseClient;

    private static final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeAll
    static void createTopics() {
        Map<String, Object> adminProps = Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()
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

    @Test
    void consumesPaymentRequested_andPersistsPaymentAndAttempts() throws Exception {
        // 1) Successful payment request
        String checkoutId1 = UUID.randomUUID().toString();
        String orderId1 = UUID.randomUUID().toString();
        String customerId1 = UUID.randomUUID().toString();

        UUID correlationId1 = UUID.randomUUID();
        publishPaymentRequested(correlationId1, checkoutId1, orderId1, customerId1, new BigDecimal("123.45"), "EUR");

        awaitPaymentAndLatestAttempt(checkoutId1, PaymentStatus.SUCCEEDED, PaymentStatus.SUCCEEDED, null);

        // 2) Forced FAIL payment request
        String checkoutId2 = UUID.randomUUID().toString();
        String orderId2 = UUID.randomUUID().toString();
        String customerId2 = UUID.randomUUID().toString();

        UUID correlationId2 = UUID.randomUUID();
        publishPaymentRequested(correlationId2, checkoutId2, orderId2, customerId2, new BigDecimal("50.00"), "FAIL");

        awaitPaymentAndLatestAttempt(checkoutId2, PaymentStatus.FAILED, PaymentStatus.FAILED, "Forced FAIL for testing");
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
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
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
                    .sql("select attempt_no, status, reason from payment_attempt where payment_id = :paymentId order by attempt_no desc limit 1")
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

            if (expectedPaymentStatus.equals(paymentStatus) && expectedAttemptStatus.equals(attemptStatus)) {
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

    private static void sleep(Duration d) {
        try {
            Thread.sleep(d.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
