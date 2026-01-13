package rs.master.o2c.infra.web.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

public abstract class AbstractGlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public Mono<ResponseEntity<ApiErrorResponse>> handleResponseStatus(ResponseStatusException ex, ServerHttpRequest request) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return Mono.just(build(status, ex.getReason(), request, null));
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<ApiErrorResponse>> handleValidation(WebExchangeBindException ex, ServerHttpRequest request) {
        List<ApiErrorResponse.FieldError> fields = ex.getFieldErrors().stream()
                .map(this::mapFieldError)
                .toList();
        return Mono.just(build(HttpStatus.BAD_REQUEST, "Validation failed", request, fields));
    }

    @ExceptionHandler(ServerWebInputException.class)
    public Mono<ResponseEntity<ApiErrorResponse>> handleWebInput(ServerWebInputException ex, ServerHttpRequest request) {
        String message = ex.getReason() != null ? ex.getReason() : "Invalid request";
        return Mono.just(build(HttpStatus.BAD_REQUEST, message, request, null));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Mono<ResponseEntity<ApiErrorResponse>> handleIllegalArgument(IllegalArgumentException ex, ServerHttpRequest request) {
        return Mono.just(build(HttpStatus.BAD_REQUEST, ex.getMessage(), request, null));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public Mono<ResponseEntity<ApiErrorResponse>> handleAccessDenied(AccessDeniedException ex, ServerHttpRequest request) {
        return Mono.just(build(HttpStatus.FORBIDDEN, "Forbidden", request, null));
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ApiErrorResponse>> handleUnhandled(Exception ex, ServerHttpRequest request) {
        return Mono.just(build(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error", request, null));
    }

    private ResponseEntity<ApiErrorResponse> build(
            HttpStatus status,
            String message,
            ServerHttpRequest request,
            List<ApiErrorResponse.FieldError> fieldErrors
    ) {
        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getPath().value(),
                request.getId(),
                fieldErrors
        );

        return ResponseEntity.status(status).body(body);
    }

    private ApiErrorResponse.FieldError mapFieldError(FieldError fe) {
        String field = fe.getField();
        String message = fe.getDefaultMessage();
        return new ApiErrorResponse.FieldError(field, message);
    }
}
