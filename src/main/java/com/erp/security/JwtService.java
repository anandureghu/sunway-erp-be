package com.erp.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

@Service
public class JwtService {
    private final Key    key;
    private final String issuer;
    private final long   accessMinutes;
    private final long   refreshDays;

    public JwtService(
            @Value("${app.jwt.secret}")                String secret,
            @Value("${app.jwt.issuer}")                String issuer,
            @Value("${app.jwt.access-token-minutes}")  long accessMinutes,
            @Value("${app.jwt.refresh-token-days}")    long refreshDays) {

        this.key           = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer        = issuer;
        this.accessMinutes = accessMinutes;
        this.refreshDays   = refreshDays;
    }

    public String generateAccessToken(String subject, Map<String, Object> claims) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuer(issuer)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusSeconds(accessMinutes * 60)))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateRefreshToken(String subject) {
        return generateRefreshToken(subject, Map.of());
    }

    public String generateRefreshToken(String subject, Map<String, Object> claims) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuer(issuer)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusSeconds(refreshDays * 86400)))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public Jws<Claims> parse(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .setAllowedClockSkewSeconds(60)
                .build()
                .parseClaimsJws(token);
    }

    public Long extractUserId(String token) {
        try {
            Object id = parse(token).getBody().get("userId");
            if (id == null)                  return null;
            if (id instanceof Integer i)     return i.longValue();
            if (id instanceof Long l)        return l;
            return Long.parseLong(String.valueOf(id));
        } catch (Exception e) {
            return null;
        }
    }
}