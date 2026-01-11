package rs.master.o2c.auth.bff;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import rs.master.o2c.auth.config.AuthJwtProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "bff.cookie")
public record BffCookieProperties(
        Boolean secure,
        String sameSite,
        String path,
        Boolean httpOnly
) {
    public BffCookieProperties {
        if (secure == null) {
            secure = true;
        }
        if (sameSite == null || sameSite.isBlank()) {
            sameSite = "Lax";
        }
        if (path == null || path.isBlank()) {
            path = "/";
        }
        if (httpOnly == null) {
            httpOnly = true;
        }
    }

    public Duration sessionMaxAge(AuthJwtProperties jwtProps) {
        return Duration.ofMinutes(jwtProps.expiresInMinutes());
    }

    public ResponseCookie sessionCookie(String name, String value, Duration maxAge, ServerHttpRequest request) {
        return ResponseCookie.from(name, value)
                .httpOnly(Boolean.TRUE.equals(httpOnly))
                .secure(effectiveSecure(request))
                .path(path)
                .sameSite(sameSite)
                .maxAge(maxAge)
                .build();
    }

    public ResponseCookie expiredCookie(String name, ServerHttpRequest request) {
        return ResponseCookie.from(name, "")
                .httpOnly(Boolean.TRUE.equals(httpOnly))
                .secure(effectiveSecure(request))
                .path(path)
                .sameSite(sameSite)
                .maxAge(Duration.ZERO)
                .build();
    }

    private boolean effectiveSecure(ServerHttpRequest request) {
        boolean configuredSecure = Boolean.TRUE.equals(secure);
        if (request == null) {
            return configuredSecure;
        }
        String scheme = request.getURI().getScheme();
        return configuredSecure || "https".equalsIgnoreCase(scheme);
    }
}
