package com.erp.controller;

import com.erp.dto.security.ChangePasswordRequest;
import com.erp.dto.security.ProfileResponse;
import com.erp.dto.hr.UserDetailsDTO;
import com.erp.repo.UserRepository;
import com.erp.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository repo;
    private final UserService    userService;

    public UserController(UserRepository repo, UserService userService) {
        this.repo        = repo;
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<?> list() {
        return ResponseEntity.ok(repo.findAll());
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
}