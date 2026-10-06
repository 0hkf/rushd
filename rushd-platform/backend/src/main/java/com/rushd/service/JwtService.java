package com.rushd.service;

import com.rushd.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;
import java.security.Key;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {
    private final JwtProperties policy;
    private final Clock clock;
    private final Key key;

    public JwtService(JwtProperties policy, Clock clock) {
        this.policy = policy;
        this.clock = clock;
        this.key = Keys.hmacShaKeyFor(policy.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(String email) {
        Instant now = clock.instant();
        return Jwts.builder().setSubject(email).claim("token_type", "access")
                .setIssuedAt(Date.from(now)).setExpiration(Date.from(now.plus(policy.accessTokenTtl())))
                .signWith(key, SignatureAlgorithm.HS256).compact();
    }

    public Claims validateAccessToken(String token) {
        Claims claims = Jwts.parserBuilder().setSigningKey(key)
                .setClock(() -> Date.from(clock.instant())).build().parseClaimsJws(token).getBody();
        if (!"access".equals(claims.get("token_type", String.class))
                || claims.getSubject() == null || claims.getSubject().isBlank()
                || claims.getExpiration() == null || !claims.getExpiration().toInstant().isAfter(clock.instant())) {
            throw new JwtException("Invalid access credential");
        }
        return claims;
    }
}
