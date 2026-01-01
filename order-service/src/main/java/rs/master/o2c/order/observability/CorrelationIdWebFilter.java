package rs.master.o2c.order.observability;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import rs.master.o2c.events.CorrelationHeaders;

import java.util.UUID;

@Component
public class CorrelationIdWebFilter implements WebFilter {
	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		String correlationId = exchange.getRequest().getHeaders().getFirst(CorrelationHeaders.X_CORRELATION_ID);
		if (correlationId == null || correlationId.trim().isEmpty()) {
			correlationId = UUID.randomUUID().toString();
		}

		String finalCorrelationId = correlationId;
		exchange.getResponse().beforeCommit(() -> {
			exchange.getResponse().getHeaders().set(CorrelationHeaders.X_CORRELATION_ID, finalCorrelationId);
			return Mono.empty();
		});

		ServerHttpRequest mutatedRequest = exchange.getRequest()
				.mutate()
				.headers(headers -> headers.set(CorrelationHeaders.X_CORRELATION_ID, finalCorrelationId))
				.build();

		ServerWebExchange mutatedExchange = exchange.mutate().request(mutatedRequest).build();
		mutatedExchange.getAttributes().put(CorrelationHeaders.X_CORRELATION_ID, finalCorrelationId);

		return chain.filter(mutatedExchange)
				.contextWrite(ctx -> ctx.put(CorrelationHeaders.X_CORRELATION_ID, finalCorrelationId));
	}
}
