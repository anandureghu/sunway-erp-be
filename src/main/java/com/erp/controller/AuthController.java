package com.erp.controller;

import com.erp.domain.User;
import com.erp.dto.auth.*;
import com.erp.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController @RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    public AuthController(AuthService auth) { this.auth = auth; }

    @PostMapping("/register")
    public ResponseEntity<User> register(@RequestBody @Valid RegisterRequest req) { return ResponseEntity.ok(auth.register(req)); }

    @PostMapping("/login")
    public ResponseEntity<JwtResponse> login(@RequestBody @Valid LoginRequest req) {
        Map<String, String> t = auth.login(req);
        return ResponseEntity.ok(new JwtResponse(t.get("accessToken"), t.get("refreshToken")));
    }

    @PostMapping("/refresh")
    public ResponseEntity<JwtResponse> refresh(@RequestBody @Valid RefreshTokenRequest req) {
        Map<String, String> t = auth.refresh(req.getRefreshToken());
        return ResponseEntity.ok(new JwtResponse(t.get("accessToken"), t.get("refreshToken")));
    }
}
