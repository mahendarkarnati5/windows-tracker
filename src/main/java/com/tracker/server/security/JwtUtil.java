package com.tracker.server.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    public String generateToken(String username, Long userId, String role) {
        return Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(
                        new Date(
                                System.currentTimeMillis()
                                        + expiration))
                .signWith(getSignKey())
                .compact();
    }

    public String generateToken(String username, Long userId) {
        return generateToken(username, userId, "USER");
    }
    private SecretKey getSignKey() {

        return Keys.hmacShaKeyFor(
                secret.getBytes(
                        StandardCharsets.UTF_8));
    }
    
    
    private Claims extractClaims(
            String token) {

        return Jwts.parser()
                .verifyWith(getSignKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
    
    private boolean isTokenExpired(
            String token) {

        return extractClaims(token)
                .getExpiration()
                .before(new Date());
    }
    
    public String extractUsername(
            String token) {

        return extractClaims(token)
                .getSubject();
    }

    public boolean validateToken(
            String token,
            String username) {

        return extractUsername(token)
                .equals(username)
                && !isTokenExpired(token);
    }
    public Long extractUserId(
            String token) {

        return extractClaims(token)
                .get("userId", Long.class);
    }

    public String extractRole(String token) {
        return extractClaims(token).get("role", String.class);
    }
}
