package com.erp.controller;

import com.erp.dto.hr.UserDetailsDTO;
import com.erp.repo.UserRepository;
import com.erp.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository repo;
    private final UserService userService;

    public UserController(UserRepository repo, UserService userService) {
        this.repo = repo;
        this.userService = userService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<?> list() {
        return ResponseEntity.ok(repo.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserDetailsDTO> getDetails(@PathVariable("id") Long id) {
        return ResponseEntity.ok(userService.getUserDetails(id));
    }
}
