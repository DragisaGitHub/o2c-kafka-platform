package rs.master.o2c.auth.config;

import org.springframework.http.HttpStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Mono;
import rs.master.o2c.auth.bff.BffCorsProperties;
import rs.master.o2c.auth.security.BffSessionAuthenticationConverter;

import java.util.List;

@Configuration
@EnableReactiveMethodSecurity
public class SecurityConfig {

        @Bean
        public ReactiveUserDetailsService reactiveUserDetailsService() {
                // Prevent Spring Boot from auto-creating/logging a default user/password.
                // This app uses cookie-based sessions + JWT parsing for authentication.
                return username -> Mono.empty();
        }

        @Bean
        public CorsConfigurationSource corsConfigurationSource(BffCorsProperties props) {
                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

                if (props.allowedOrigins() == null || props.allowedOrigins().isEmpty()) {
                        return source;
                }

                CorsConfiguration cfg = new CorsConfiguration();
                cfg.setAllowedOrigins(props.allowedOrigins());
                cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
                cfg.setAllowedHeaders(List.of("*"));
                cfg.setExposedHeaders(List.of("Set-Cookie"));
                cfg.setAllowCredentials(true);

                source.registerCorsConfiguration("/**", cfg);
                return source;
        }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http, BffSessionAuthenticationConverter bffAuthConverter) {
    ReactiveAuthenticationManager authManager = Mono::just;

    AuthenticationWebFilter authWebFilter = new AuthenticationWebFilter(authManager);
    authWebFilter.setServerAuthenticationConverter(bffAuthConverter);
    authWebFilter.setRequiresAuthenticationMatcher(ServerWebExchangeMatchers.pathMatchers("/api/**"));

        return http
                .cors(Customizer.withDefaults())
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .exceptionHandling(ex -> ex.authenticationEntryPoint((exchange, e) -> {
                        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                        return exchange.getResponse().setComplete();
                }))
        .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .authorizeExchange(ex -> ex
                        .pathMatchers("/actuator/**").permitAll()
            .pathMatchers("/").permitAll()
                        .pathMatchers("/logout").permitAll()
            .pathMatchers("/auth/**").permitAll()
            .pathMatchers("/mfa/ott/sent").permitAll()
            .pathMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
            .pathMatchers("/api/**").authenticated()
            .anyExchange().permitAll()
                )
        .addFilterAt(authWebFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }
}