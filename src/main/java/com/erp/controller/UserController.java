package com.erp.controller;

import com.erp.domain.User;
import com.erp.dto.security.AdminResetPasswordRequest;
import com.erp.dto.security.ChangePasswordRequest;
import com.erp.dto.security.ProfileResponse;
import com.erp.dto.hr.UserDetailsDTO;
import com.erp.repo.UserRepository;
import com.erp.security.context.AuthContext;
import com.erp.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository repo;
    private final UserService userService;
    private final AuthContext authContext;

    public UserController(UserRepository repo, UserService userService, AuthContext authContext) {
        this.repo = repo;
        this.userService = userService;
        this.authContext = authContext;
    }

    @GetMapping
    public ResponseEntity<List<User>> list() {
        Long companyId = authContext.getCurrentCompanyId();
        if (companyId == null) {
            return ResponseEntity.ok(Collections.emptyList());
        }
        return ResponseEntity.ok(repo.findByCompany_Id(companyId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserDetailsDTO> getDetails(@PathVariable("id") Long id) {
        return ResponseEntity.ok(userService.getUserDetails(id));
    }

    @GetMapping("/{id}/profile")
    public ResponseEntity<ProfileResponse> getProfile(@PathVariable("id") Long id) {
        return ResponseEntity.ok(userService.getProfile(id));
    }

    @PutMapping("/{id}/password")
    public ResponseEntity<Void> changePassword(
            @PathVariable("id") Long id,
            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(id, request);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/admin-reset-password")
    public ResponseEntity<Void> adminResetPassword(
            @PathVariable("id") Long id,
            @Valid @RequestBody AdminResetPasswordRequest request) {
        userService.adminResetPassword(id, request);
        return ResponseEntity.ok().build();
    }
}
