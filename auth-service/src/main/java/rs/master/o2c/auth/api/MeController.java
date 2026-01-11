package rs.master.o2c.auth.api;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api")
public class MeController {

    public record MeResponse(String username, List<String> roles) {}

    @GetMapping("/me")
    public Mono<ResponseEntity<MeResponse>> me() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(auth -> auth != null && auth.isAuthenticated())
                .map(auth -> ResponseEntity.ok(new MeResponse(auth.getName(), rolesFrom(auth))))
                .switchIfEmpty(Mono.just(ResponseEntity.status(401).build()));
    }

    private static List<String> rolesFrom(Authentication auth) {
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .map(r -> r.startsWith("ROLE_") ? r.substring("ROLE_".length()) : r)
                .distinct()
                .toList();
    }
}
