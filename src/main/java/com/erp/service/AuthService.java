package com.erp.service;

import com.erp.domain.Employee;
import com.erp.domain.User;
import com.erp.domain.hr.Company;
import com.erp.domain.security.Role;
import com.erp.dto.auth.CompanySummary;
import com.erp.dto.auth.JwtResponse;
import com.erp.dto.auth.LoginRequest;
import com.erp.dto.auth.RegisterRequest;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.UserRepository;
import com.erp.security.JwtService;
import com.erp.security.context.AuthContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final AuthContext authContext;

    public AuthService(UserRepository userRepository,
                       EmployeeRepository employeeRepository,
                       PasswordEncoder encoder,
                       JwtService jwt,
                       AuthContext authContext) {
        this.userRepository     = userRepository;
        this.employeeRepository = employeeRepository;
        this.encoder            = encoder;
        this.jwt                = jwt;
        this.authContext        = authContext;
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

    public JwtResponse login(LoginRequest req) {
        User u = userRepository.findByEmail(req.getLoginId())
                .or(() -> userRepository.findByUsername(req.getLoginId()))
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        if (!encoder.matches(req.getPassword(), u.getPassword()))
            throw new IllegalArgumentException("Invalid credentials");

        List<Employee> memberships = employeeRepository.findAllByUser_Id(u.getId());
        if (memberships.isEmpty())
            throw new IllegalArgumentException("Invalid credentials");

        List<CompanySummary> companies = memberships.stream()
                .map(this::toCompanySummary)
                .toList();

        Employee active = resolveActiveEmployee(memberships, req.getPreferredCompanyId());
        boolean requiresSelection = memberships.size() > 1 && req.getPreferredCompanyId() == null;

        return buildJwtResponse(u, active, companies, requiresSelection);
    }

    public JwtResponse refresh(String refreshToken) {
        var claims = jwt.parse(refreshToken).getBody();
        String username = claims.getSubject();

        User u = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found for refresh"));

        Long employeeId = parseLong(claims.get("employeeId"));
        Employee emp;
        if (employeeId != null) {
            emp = employeeRepository.findById(employeeId)
                    .filter(e -> e.getUser() != null && e.getUser().getId().equals(u.getId()))
                    .orElseThrow(() -> new IllegalArgumentException("User not found for refresh"));
        } else {
            emp = employeeRepository.findAllByUser_Id(u.getId()).stream()
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("User not found for refresh"));
        }

        List<CompanySummary> companies = employeeRepository.findAllByUser_Id(u.getId()).stream()
                .map(this::toCompanySummary)
                .toList();

        return buildJwtResponse(u, emp, companies, false);
    }

    public List<CompanySummary> getMyCompanies() {
        Long userId = authContext.getCurrentUserId();
        if (userId == null) throw new IllegalArgumentException("Unauthorized");

        return employeeRepository.findAllByUser_Id(userId).stream()
                .map(this::toCompanySummary)
                .toList();
    }

    public JwtResponse switchCompany(Long companyId) {
        Long userId = authContext.getCurrentUserId();
        if (userId == null) throw new IllegalArgumentException("Unauthorized");

        User u = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Employee emp = employeeRepository.findByUser_IdAndCompany_Id(userId, companyId)
                .orElseThrow(() -> new IllegalArgumentException("You do not belong to this company"));

        List<CompanySummary> companies = employeeRepository.findAllByUser_Id(userId).stream()
                .map(this::toCompanySummary)
                .toList();

        return buildJwtResponse(u, emp, companies, false);
    }

    public String hash(String raw) {
        return encoder.encode(raw);
    }

    private JwtResponse buildJwtResponse(User u, Employee emp, List<CompanySummary> companies, boolean requiresSelection) {
        Map<String, Object> accessClaims = buildAccessClaims(u, emp);
        Map<String, Object> refreshClaims = new HashMap<>();
        refreshClaims.put("employeeId", emp.getId());
        refreshClaims.put("userId", u.getId());

        String access  = jwt.generateAccessToken(u.getUsername(), accessClaims);
        String refresh = jwt.generateRefreshToken(u.getUsername(), refreshClaims);

        JwtResponse response = new JwtResponse(access, refresh);
        response.setCompanies(companies);
        response.setRequiresCompanySelection(requiresSelection);
        return response;
    }

    private Map<String, Object> buildAccessClaims(User u, Employee emp) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", u.getId());
        claims.put("employeeId", emp.getId());
        claims.put("username", u.getUsername());
        claims.put("email", u.getEmail());
        claims.put("companyId", emp.getCompany().getId());
        claims.put("role", u.getRole().name());
        claims.put("companyRoleId", emp.getCompanyRoleId());
        claims.put("companyRole", emp.getCompanyRole());
        return claims;
    }

    private Employee resolveActiveEmployee(List<Employee> memberships, Long preferredCompanyId) {
        if (preferredCompanyId != null) {
            return memberships.stream()
                    .filter(e -> e.getCompany() != null && preferredCompanyId.equals(e.getCompany().getId()))
                    .findFirst()
                    .orElse(memberships.getFirst());
        }
        return memberships.getFirst();
    }

    private CompanySummary toCompanySummary(Employee emp) {
        Company c = emp.getCompany();
        return CompanySummary.builder()
                .id(c != null ? c.getId() : null)
                .companyName(c != null ? c.getCompanyName() : null)
                .logoUrl(c != null ? c.getLogoUrl() : null)
                .build();
    }

    private Long parseLong(Object value) {
        if (value == null) return null;
        if (value instanceof Integer i) return i.longValue();
        if (value instanceof Long l) return l;
        return Long.parseLong(String.valueOf(value));
    }
}
