package rs.master.o2c.checkout.api.error;

import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import rs.master.o2c.infra.web.error.AbstractGlobalExceptionHandler;

@RestControllerAdvice
@Order(-2)
public class GlobalExceptionHandler extends AbstractGlobalExceptionHandler {}
