package rs.master.o2c.checkout.api.dto;

import java.time.Instant;

public record CheckoutTimelineEventDto(String type, String status, Instant at) {}
