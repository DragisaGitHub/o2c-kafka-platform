package rs.master.o2c.events;

import java.math.BigDecimal;

public record Money(
        String currency,
        BigDecimal amount
) {}