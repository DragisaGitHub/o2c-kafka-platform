package rs.master.o2c.events;

public final class TopicNames {
    private TopicNames() {}

    public static final String ORDER_EVENTS_V1 = "order.events.v1";
    public static final String ORDER_EVENTS_DLQ_V1 = "order.events.dlq.v1";
    public static final String CHECKOUT_EVENTS_V1 = "checkout.events.v1";
    public static final String CHECKOUT_EVENTS_DLQ_V1 = "checkout.events.dlq.v1";

    public static final String PAYMENT_REQUESTS_V1 = "payment.requests.v1";
    public static final String PAYMENT_REQUESTS_DLQ_V1 = "payment.requests.dlq.v1";
    public static final String PAYMENT_EVENTS_V1 = "payment.events.v1";
    public static final String PAYMENT_EVENTS_DLQ_V1 = "payment.events.dlq.v1";
}