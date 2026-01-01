package rs.master.o2c.order.messaging.handler;

import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import rs.master.o2c.events.checkout.CheckoutCompleted;
import rs.master.o2c.events.checkout.CheckoutFailed;
import rs.master.o2c.events.EventTypes;
import rs.master.o2c.events.order.OrderStatus;
import rs.master.o2c.order.persistence.entity.InboxProcessedEntity;
import rs.master.o2c.order.persistence.repository.InboxProcessedRepository;
import rs.master.o2c.order.persistence.repository.OrderRepository;

@Component
@RequiredArgsConstructor
public class CheckoutEventsHandler {

    private final ObjectMapper objectMapper;
    private final OrderRepository orderRepository;
    private final InboxProcessedRepository inboxProcessedRepository;

    public Mono<Void> handle(String payload) {
        return Mono.fromCallable(() -> objectMapper.readTree(payload))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(root -> {
                    String eventType = text(root, "eventType");
                    if (!EventTypes.CHECKOUT_COMPLETED.equals(eventType)
                            && !EventTypes.CHECKOUT_FAILED.equals(eventType)) {
                        return Mono.empty();
                    }

                    String messageId = text(root, "messageId");

                    return inboxProcessedRepository
                            .save(new InboxProcessedEntity(messageId, Instant.now()))
                            .then(process(root, eventType))
                            .onErrorResume(DuplicateKeyException.class, e -> Mono.empty());
                });
    }

    private Mono<Void> process(JsonNode root, String eventType) {
        if (EventTypes.CHECKOUT_COMPLETED.equals(eventType)) {
            CheckoutCompleted ev = objectMapper.convertValue(root.get("payload"), CheckoutCompleted.class);

            return orderRepository
                    .findById(ev.orderId())
                    .flatMap(order -> {
                        order.setStatus(OrderStatus.CONFIRMED);
                        order.markNotNew();
                        return orderRepository.save(order);
                    })
                    .then();
        }

        CheckoutFailed ev = objectMapper.convertValue(root.get("payload"), CheckoutFailed.class);

        return orderRepository
                .findById(ev.orderId())
                .flatMap(order -> {
                    order.setStatus(OrderStatus.FAILED);
                    order.markNotNew();
                    return orderRepository.save(order);
                })
                .then();
    }

    private String text(JsonNode root, String field) {
        JsonNode n = root.get(field);
        return n == null || n.isNull() ? null : n.asText();
    }
}