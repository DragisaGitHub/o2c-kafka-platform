package rs.master.o2c.auth.bff;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Set;

@RestController
public class BffProxyController {

    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "connection",
            "keep-alive",
            "proxy-authenticate",
            "proxy-authorization",
            "te",
            "trailers",
            "transfer-encoding",
            "upgrade"
    );

    private final WebClient webClient;
    private final BffUpstreamProperties upstreams;

    public BffProxyController(BffUpstreamProperties upstreams, WebClient.Builder builder) {
        this.upstreams = upstreams;
        this.webClient = builder.build();
    }

    @RequestMapping("/api/{service}/**")
    public Mono<ResponseEntity<byte[]>> proxy(
            @PathVariable String service,
            org.springframework.http.server.reactive.ServerHttpRequest request,
            Authentication authentication
    ) {
        String upstreamBase = resolveUpstream(service);
        if (upstreamBase == null) {
            return Mono.just(ResponseEntity.notFound().build());
        }

        if (authentication == null || !authentication.isAuthenticated()) {
            return Mono.just(ResponseEntity.status(401).build());
        }

        String token = authentication.getCredentials() == null ? null : String.valueOf(authentication.getCredentials());
        if (!StringUtils.hasText(token)) {
            return Mono.just(ResponseEntity.status(401).build());
        }

        String fullPath = request.getPath().pathWithinApplication().value();
        String prefix = "/api/" + service;
        String downstreamPath = fullPath.startsWith(prefix) ? fullPath.substring(prefix.length()) : fullPath;
        if (!StringUtils.hasText(downstreamPath)) {
            downstreamPath = "/";
        }

        String rawQuery = request.getURI().getRawQuery();
        String target = upstreamBase + downstreamPath + (rawQuery == null ? "" : ("?" + rawQuery));

        HttpMethod method = request.getMethod();

        WebClient.RequestBodySpec spec = webClient
                .method(method)
                .uri(URI.create(target))
                .headers(out -> {
                    copyRequestHeaders(request.getHeaders(), out);
                    out.remove(HttpHeaders.COOKIE);
                    out.setBearerAuth(token);
                });

        boolean hasBody = method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.PATCH;
        WebClient.RequestHeadersSpec<?> headersSpec = hasBody
                ? spec.body(BodyInserters.fromDataBuffers(request.getBody()))
                : spec;

        return headersSpec.exchangeToMono(BffProxyController::toResponseEntity);
    }

    private String resolveUpstream(String service) {
        return switch (service) {
            case "order" -> upstreams.order();
            case "checkout" -> upstreams.checkout();
            case "payment" -> upstreams.payment();
            default -> null;
        };
    }

    private static void copyRequestHeaders(HttpHeaders in, HttpHeaders out) {
        in.forEach((name, values) -> {
            String lower = name.toLowerCase();
            if (HOP_BY_HOP_HEADERS.contains(lower)) return;
            if (HttpHeaders.HOST.equalsIgnoreCase(name)) return;
            if (HttpHeaders.AUTHORIZATION.equalsIgnoreCase(name)) return;
            out.put(name, values);
        });
    }

    private static Mono<ResponseEntity<byte[]>> toResponseEntity(ClientResponse resp) {
        HttpHeaders outHeaders = new HttpHeaders();
        resp.headers().asHttpHeaders().forEach((name, values) -> {
            String lower = name.toLowerCase();
            if (HOP_BY_HOP_HEADERS.contains(lower)) return;
            if (HttpHeaders.SET_COOKIE.equalsIgnoreCase(name)) return;
            outHeaders.put(name, values);
        });

        return resp
                .bodyToMono(byte[].class)
                .defaultIfEmpty(new byte[0])
                .map(body -> ResponseEntity.status(resp.statusCode()).headers(outHeaders).body(body));
    }
}
