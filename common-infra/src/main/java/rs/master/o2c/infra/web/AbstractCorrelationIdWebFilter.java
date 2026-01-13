package rs.master.o2c.infra.web;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import rs.master.o2c.events.CorrelationHeaders;
import rs.master.o2c.infra.correlation.CorrelationIdSupport;

/**
 * Shared implementation of the per-service CorrelationId web filter.
 *
 * Note: this intentionally preserves the original correlationId value if present (no trim),
 * matching the existing service behavior.
 */
public abstract class AbstractCorrelationIdWebFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String correlationId = exchange.getRequest().getHeaders().getFirst(CorrelationHeaders.X_CORRELATION_ID);
        String finalCorrelationId = CorrelationIdSupport.ensureOrGenerate(correlationId);

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
