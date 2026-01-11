package rs.master.o2c.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import rs.master.o2c.auth.bff.BffSessionService;
import rs.master.o2c.auth.config.AuthJwtProperties;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Component
public class BffSessionAuthenticationConverter implements ServerAuthenticationConverter {

    private static final String EXPECTED_JWT_ALG = "HS384";

    private final BffSessionService sessions;
    private final SecretKey key;

    public BffSessionAuthenticationConverter(BffSessionService sessions, AuthJwtProperties jwtProps) {
        this.sessions = sessions;
        this.key = Keys.hmacShaKeyFor(jwtProps.secret().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Mono<Authentication> convert(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        var cookie = request.getCookies().getFirst(BffSessionService.COOKIE_NAME);
        if (cookie == null) {
            return Mono.empty();
        }

        var session = sessions.getValidSession(cookie.getValue());
        if (session == null || !StringUtils.hasText(session.accessToken())) {
            return Mono.empty();
        }

        String token = session.accessToken();

        try {
            Jws<Claims> parsed = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);

            String alg = parsed.getHeader().getAlgorithm();
            if (!EXPECTED_JWT_ALG.equals(alg)) {
                return Mono.error(new BadCredentialsException("Unexpected JWT alg: " + alg));
            }

            Claims claims = parsed.getPayload();
            String username = claims.getSubject();
            if (!StringUtils.hasText(username)) {
                return Mono.error(new BadCredentialsException("JWT subject is missing"));
            }

            List<String> roles = extractRoles(claims.get("roles"));
            List<GrantedAuthority> authorities = roles.stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .map(r -> "ROLE_" + r)
                    .map(r -> (GrantedAuthority) new SimpleGrantedAuthority(r))
                    .toList();

            return Mono.just(new UsernamePasswordAuthenticationToken(username, token, authorities));
        } catch (Exception e) {
            return Mono.error(new BadCredentialsException("Invalid JWT", e));
        }
    }

    private static List<String> extractRoles(Object claimValue) {
        if (claimValue == null) {
            return List.of();
        }

        if (claimValue instanceof Collection<?> col) {
            List<String> out = new ArrayList<>(col.size());
            for (Object v : col) {
                if (v != null) {
                    out.add(String.valueOf(v));
                }
            }
            return out;
        }

        if (claimValue.getClass().isArray()) {
            Object[] arr = (Object[]) claimValue;
            List<String> out = new ArrayList<>(arr.length);
            for (Object v : arr) {
                if (v != null) {
                    out.add(String.valueOf(v));
                }
            }
            return out;
        }

        return List.of(String.valueOf(claimValue));
    }
}
