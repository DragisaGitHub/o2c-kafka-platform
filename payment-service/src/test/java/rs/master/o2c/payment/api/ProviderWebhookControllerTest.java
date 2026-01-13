package rs.master.o2c.payment.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import rs.master.o2c.events.payment.PaymentStatus;
import rs.master.o2c.payment.config.SecurityConfig;
import rs.master.o2c.payment.kafka.PaymentEventPublisher;
import rs.master.o2c.payment.observability.CorrelationIdWebFilter;
import rs.master.o2c.payment.persistence.entity.PaymentAttemptEntity;
import rs.master.o2c.payment.persistence.entity.PaymentEntity;
import rs.master.o2c.payment.persistence.repository.PaymentAttemptRepository;
import rs.master.o2c.payment.persistence.repository.PaymentRepository;
import rs.master.o2c.payment.impl.ProviderWebhookServiceImpl;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@WebFluxTest(controllers = ProviderWebhookController.class)
@Import({SecurityConfig.class, CorrelationIdWebFilter.class, ProviderWebhookServiceImpl.class})
@SuppressWarnings({"null", "removal"})
class ProviderWebhookControllerTest {

    @Autowired
    WebTestClient webTestClient;

    @MockBean
    PaymentAttemptRepository paymentAttemptRepository;

    @MockBean
    PaymentRepository paymentRepository;

    @MockBean
    PaymentEventPublisher paymentEventPublisher;

        @MockBean
        ReactiveJwtDecoder reactiveJwtDecoder;

    @Test
    void webhook_shouldReturn202_whenProviderPaymentIdUnknown() {
        String providerPaymentId = UUID.randomUUID().toString();

        when(paymentAttemptRepository.findByProviderPaymentId(providerPaymentId)).thenReturn(Mono.empty());

        webTestClient.post()
                .uri("/webhooks/provider/payments")
                .bodyValue(new ProviderWebhookController.ProviderPaymentWebhookRequest(providerPaymentId, "SUCCEEDED", null))
                .exchange()
                .expectStatus().isAccepted();

        verify(paymentAttemptRepository, times(1)).findByProviderPaymentId(providerPaymentId);
        verifyNoInteractions(paymentRepository);
    }

    @Test
    void webhook_shouldBeIdempotent_whenAttemptAlreadyTerminal() {
        String providerPaymentId = UUID.randomUUID().toString();

        PaymentAttemptEntity attempt = new PaymentAttemptEntity(
                10L,
                "pay-1",
                1,
                PaymentStatus.SUCCEEDED,
                null,
                providerPaymentId,
                Instant.now(),
                Instant.now()
        );

        when(paymentAttemptRepository.findByProviderPaymentId(providerPaymentId)).thenReturn(Mono.just(attempt));

        webTestClient.post()
                .uri("/webhooks/provider/payments")
                .bodyValue(new ProviderWebhookController.ProviderPaymentWebhookRequest(providerPaymentId, "SUCCEEDED", null))
                .exchange()
                .expectStatus().isAccepted();

        verify(paymentAttemptRepository, never()).updateStatusAndReasonById(any(Long.class), any(), any());
        verifyNoInteractions(paymentRepository);
        verifyNoInteractions(paymentEventPublisher);
    }

    @Test
    void webhook_shouldUpdateAttemptAndPayment_andPublishOutcome() {
        String providerPaymentId = UUID.randomUUID().toString();

        PaymentAttemptEntity attempt = new PaymentAttemptEntity(
                10L,
                "pay-1",
                1,
                PaymentStatus.PENDING,
                null,
                providerPaymentId,
                Instant.now(),
                null
        );

        PaymentEntity payment = new PaymentEntity(
                "pay-1",
                "order-1",
                "chk-1",
                "cust-1",
                PaymentStatus.PENDING,
                new BigDecimal("12.34"),
                "EUR",
                "MOCK",
                null,
                null,
                Instant.now(),
                null
        );
        payment.markNotNew();

        when(paymentAttemptRepository.findByProviderPaymentId(providerPaymentId)).thenReturn(Mono.just(attempt));
        when(paymentAttemptRepository.updateStatusAndReasonById(eq(10L), eq(PaymentStatus.SUCCEEDED), eq(null))).thenReturn(Mono.just(1));
        when(paymentRepository.findById("pay-1")).thenReturn(Mono.just(payment));
        when(paymentRepository.save(any(PaymentEntity.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(paymentEventPublisher.publishPaymentEvent(any(), any())).thenReturn(Mono.empty());

        webTestClient.post()
                .uri("/webhooks/provider/payments")
                .bodyValue(new ProviderWebhookController.ProviderPaymentWebhookRequest(providerPaymentId, "SUCCEEDED", null))
                .exchange()
                .expectStatus().isAccepted();

        verify(paymentAttemptRepository).updateStatusAndReasonById(eq(10L), eq(PaymentStatus.SUCCEEDED), eq(null));

        ArgumentCaptor<PaymentEntity> savedCaptor = ArgumentCaptor.forClass(PaymentEntity.class);
        verify(paymentRepository).save(savedCaptor.capture());
        PaymentEntity savedPayment = savedCaptor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals(PaymentStatus.SUCCEEDED, savedPayment.status());
        org.junit.jupiter.api.Assertions.assertEquals(providerPaymentId, savedPayment.providerPaymentId());

        verify(paymentEventPublisher, times(1)).publishPaymentEvent(eq("order-1"), any());
    }
}
