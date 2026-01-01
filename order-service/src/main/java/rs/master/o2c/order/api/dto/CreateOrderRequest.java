package rs.master.o2c.order.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateOrderRequest(
        UUID customerId,
        BigDecimal totalAmount,
        String currency
) {}