package rs.master.o2c.auth.api.dto;

public record LoginResponse(
        String status,
        String challengeId,
        String pin
) {}