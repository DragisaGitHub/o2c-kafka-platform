package rs.master.o2c.order.api.dto;

import java.util.UUID;

public record CreateOrderResponse(
        UUID orderId
) {}