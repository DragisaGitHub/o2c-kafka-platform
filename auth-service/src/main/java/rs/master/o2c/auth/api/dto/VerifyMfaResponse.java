package rs.master.o2c.auth.api.dto;

public record VerifyMfaResponse(
        String status,
        String accessToken
) {}