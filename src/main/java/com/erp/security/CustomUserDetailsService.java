package com.erp.security;

import com.erp.domain.User;
import com.erp.repo.UserRepository;
import com.erp.service.security.CustomUserPrincipal;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String usernameOrEmail)
            throws UsernameNotFoundException {

        // 🔹 1. Fetch user
        User user = userRepository.findByEmail(usernameOrEmail)
                .or(() -> userRepository.findByUsername(usernameOrEmail))
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        // 🔥 2. DIRECTLY use companyRole from User
        String companyRole = user.getCompanyRole();

        // 🔍 DEBUG (REMOVE AFTER TEST)
        System.out.println("LOGIN DEBUG → role=" + user.getRole()
                + ", companyRole=" + companyRole);

        // 🔹 3. Extract companyId
        Long companyId = user.getCompanyId();

        // 🔹 4. Build principal
        return new CustomUserPrincipal(
                user.getId(),
                user.getUsername(),
                user.getPassword(),
                user.getRole(),
                companyRole,
                companyId
        );
    }
}