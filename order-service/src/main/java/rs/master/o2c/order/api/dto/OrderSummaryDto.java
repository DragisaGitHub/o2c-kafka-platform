package rs.master.o2c.order.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderSummaryDto(
        String orderId,
        String customerId,
        String status,
        BigDecimal totalAmount,
        String currency,
        Instant createdAt
) {}
