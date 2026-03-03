package com.erp.service;

import com.erp.domain.Employee;
import com.erp.domain.security.Role;
import com.erp.domain.User;
import com.erp.dto.auth.LoginRequest;
import com.erp.dto.auth.RegisterRequest;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.UserRepository;
import com.erp.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthService(UserRepository userRepository, EmployeeRepository employeeRepository, PasswordEncoder encoder, JwtService jwt) {
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    public User register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.getEmail()))
            throw new IllegalArgumentException("Email already in use");
        if (userRepository.existsByUsername(req.getUsername()))
            throw new IllegalArgumentException("Username already in use");

        User u = new User();
        u.setFullName(req.getFullName());
        u.setEmail(req.getEmail());
        u.setUsername(req.getUsername());
        u.setPassword(encoder.encode(req.getPassword()));
        u.setRole(req.getRole() == null ? Role.USER : req.getRole());
        return userRepository.save(u);
    }

    public Map<String, String> login(LoginRequest req) {
        User u = userRepository.findByEmail(req.getLoginId())
                .or(() -> userRepository.findByUsername(req.getLoginId()))
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        Employee emp = employeeRepository
                .findByUserId(u.getId())
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        if (!encoder.matches(req.getPassword(), u.getPassword()))
            throw new IllegalArgumentException("Invalid credentials");

        // ✅ Include userId, username, and role in the token
        Map<String, Object> claims = Map.of(
                "userId", u.getId(),
                "username", u.getUsername(),
                "companyId", emp.getCompany().getId(),
                "role", u.getRole().name()
        );

        String access = jwt.generateAccessToken(u.getUsername(), claims);
        String refresh = jwt.generateRefreshToken(u.getUsername());
        return Map.of("accessToken", access, "refreshToken", refresh);
    }

    public Map<String, String> refresh(String refreshToken) {
        var claims = jwt.parse(refreshToken).getBody();
        String username = claims.getSubject();

        User u = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found for refresh"));


        Employee emp = employeeRepository
                .findByUserId(u.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found for refresh"));

        Map<String, Object> newClaims = Map.of(
                "userId", u.getId(),
                "username", u.getUsername(),
                "companyId", emp.getCompany().getId(),
                "role", u.getRole().name()
        );

        String access = jwt.generateAccessToken(u.getUsername(), newClaims);
        return Map.of("accessToken", access, "refreshToken", refreshToken);
    }

    public String hash(String raw) {
        return encoder.encode(raw);
    }
}
