package rs.master.o2c.auth.api.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyMfaRequest(
        @NotBlank String challengeId,
        @NotBlank String pin
) {}