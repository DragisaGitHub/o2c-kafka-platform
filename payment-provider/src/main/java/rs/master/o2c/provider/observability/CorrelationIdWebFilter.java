package rs.master.o2c.provider.observability;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import rs.master.o2c.events.CorrelationHeaders;

@Component
public class CorrelationIdWebFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String correlationId = exchange.getRequest().getHeaders().getFirst(CorrelationHeaders.X_CORRELATION_ID);
        if (correlationId == null || correlationId.trim().isEmpty()) {
            correlationId = UUID.randomUUID().toString();
        }

        String finalCorrelationId = correlationId.trim();

        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(r -> r.headers(headers -> headers.set(CorrelationHeaders.X_CORRELATION_ID, finalCorrelationId)))
                .build();

        mutatedExchange.getResponse().getHeaders().set(CorrelationHeaders.X_CORRELATION_ID, finalCorrelationId);
        mutatedExchange.getAttributes().put(CorrelationHeaders.X_CORRELATION_ID, finalCorrelationId);

        return chain.filter(mutatedExchange)
                .contextWrite(ctx -> ctx.put(CorrelationHeaders.X_CORRELATION_ID, finalCorrelationId));
    }
}
