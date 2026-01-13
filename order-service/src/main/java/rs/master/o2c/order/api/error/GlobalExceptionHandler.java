package rs.master.o2c.order.api.error;

import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.core.annotation.Order;
import rs.master.o2c.infra.web.error.AbstractGlobalExceptionHandler;

@RestControllerAdvice
@Order(-2)
public class GlobalExceptionHandler extends AbstractGlobalExceptionHandler {}
