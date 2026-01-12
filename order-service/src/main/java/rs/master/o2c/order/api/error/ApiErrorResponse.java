package rs.master.o2c.order.api.error;

import java.time.Instant;
import java.util.List;

public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        String requestId,
        List<FieldError> fieldErrors
) {
    public record FieldError(String field, String message) {}
}
