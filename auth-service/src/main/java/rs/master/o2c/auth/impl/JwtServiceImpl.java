package rs.master.o2c.auth.impl;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;
import rs.master.o2c.auth.config.AuthJwtProperties;
import rs.master.o2c.auth.service.JwtService;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtServiceImpl implements JwtService {

    private final SecretKey key;
    private final long expiresInMinutes;

    public JwtServiceImpl(AuthJwtProperties props) {
        this.key = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
        this.expiresInMinutes = props.expiresInMinutes();
        System.out.println("[AUTH] jwt secret length=" + props.secret().length());
    }

    @Override
    public String generateAccessToken(String username) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(expiresInMinutes * 60);

        return Jwts.builder()
                .subject(username)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .claim("mfa", true)
                .claim("roles", new String[]{"USER"})
                .signWith(key, Jwts.SIG.HS384)
                .compact();
    }
}