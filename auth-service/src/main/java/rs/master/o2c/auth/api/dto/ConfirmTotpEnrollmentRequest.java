package rs.master.o2c.auth.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ConfirmTotpEnrollmentRequest(
        @NotBlank String setupId,
        @NotBlank String code
) {}
