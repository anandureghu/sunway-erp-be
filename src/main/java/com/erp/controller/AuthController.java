package com.erp.controller;

import com.erp.domain.User;
import com.erp.dto.auth.*;
import com.erp.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    public ResponseEntity<User> register(@RequestBody @Valid RegisterRequest req) {
        return ResponseEntity.ok(auth.register(req));
    }

    @PostMapping("/login")
    public ResponseEntity<JwtResponse> login(@RequestBody @Valid LoginRequest req) {
        return ResponseEntity.ok(auth.login(req));
    }

    @PostMapping("/refresh")
    public ResponseEntity<JwtResponse> refresh(@RequestBody @Valid RefreshTokenRequest req) {
        return ResponseEntity.ok(auth.refresh(req.getRefreshToken()));
    }

    @GetMapping("/my-companies")
    public ResponseEntity<List<CompanySummary>> myCompanies() {
        return ResponseEntity.ok(auth.getMyCompanies());
    }

    @PostMapping("/switch-company")
    public ResponseEntity<JwtResponse> switchCompany(@RequestBody @Valid SwitchCompanyRequest req) {
        return ResponseEntity.ok(auth.switchCompany(req.getCompanyId()));
    }

    @GetMapping("/hash")
    public ResponseEntity<String> hash(@RequestParam("raw") String raw) {
        return ResponseEntity.ok(auth.hash(raw));
    }
}
