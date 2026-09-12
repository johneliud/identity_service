package io.github.johneliud.identity_service.config;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class JwtTokenProvider {

    private final String secret;
    private final long accessTokenExpirationMs;
    private final long refreshTokenExpirationMs;

    private javax.crypto.SecretKey signingKey;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration}") long accessTokenExpirationMs,
            @Value("${jwt.refresh-token-expiration}") long refreshTokenExpirationMs) {
        this.secret = secret;
        this.accessTokenExpirationMs = accessTokenExpirationMs;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    @PostConstruct
    public void init() {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes());
    }

    public String generateAccessToken(String userId, Set<String> roles) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenExpirationMs);

        return Jwts.builder()
                .subject(userId)
                .claim("roles", roles)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    public String generateRefreshToken() {
        byte[] bytes = new byte[64];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public long getRefreshTokenExpirationMs() {
        return refreshTokenExpirationMs;
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    public String getUserIdFromToken(String token) {
        return parseToken(token).getSubject();
    }

    public Set<String> getRolesFromToken(String token) {
        Claims claims = parseToken(token);
        Object rolesClaim = claims.get("roles");
        if (rolesClaim instanceof java.util.List<?> list) {
            return list.stream()
                    .map(Object::toString)
                    .collect(java.util.stream.Collectors.toSet());
        }
        return Set.of();
    }

    public long getAccessTokenExpirationMs() {
        return accessTokenExpirationMs;
    }
}
