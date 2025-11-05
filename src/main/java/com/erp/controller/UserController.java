package com.erp.controller;

import com.erp.domain.User;
import com.erp.repo.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/users")
public class UserController {
    private final UserRepository repo;
    public UserController(UserRepository repo) { this.repo = repo; }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping public ResponseEntity<List<User>> list() { return ResponseEntity.ok(repo.findAll()); }
}
