package com.minicord.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Utility for signing and parsing JWT authentication tokens.
 */
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expiration;

    public JwtUtil(@Value("${minicord.jwt.secret:minicord-super-secret-key-for-jwt-tokens-2026-min-32-chars-long!}") String secret,
                   @Value("${minicord.jwt.expiration:86400000}") long expiration) {
        // Ensure secret key is at least 32 bytes for HMAC-SHA256
        String paddedSecret = secret;
        while (paddedSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            paddedSecret += "0123456789abcdef";
        }
        this.key = Keys.hmacShaKeyFor(paddedSecret.getBytes(StandardCharsets.UTF_8));
        this.expiration = expiration;
    }

    public String generateToken(String userId, String username) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(userId)
                .claim("username", username)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expiration))
                .signWith(key)
                .compact();
    }

    public Claims validateAndParseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            return null;
        }
    }

    public String getUserIdFromToken(String token) {
        Claims c = validateAndParseToken(token);
        return c != null ? c.getSubject() : null;
    }

    public String getUsernameFromToken(String token) {
        Claims c = validateAndParseToken(token);
        return c != null ? c.get("username", String.class) : null;
    }
}
