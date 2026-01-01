package rs.master.o2c.events.order;

public final class OrderStatus {

    private OrderStatus() {}

    public static final String CREATED = "CREATED";
    public static final String CONFIRMED = "CONFIRMED";
    public static final String CANCELLED = "CANCELLED";
    public static final String FAILED = "FAILED";
}