package com.erp.security;

import com.erp.domain.Employee;
import com.erp.domain.User;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.UserRepository;
import com.erp.service.security.CustomUserPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;

    public CustomUserDetailsService(
            UserRepository userRepository,
            EmployeeRepository employeeRepository
    ) {
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String usernameOrEmail) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(usernameOrEmail)
                .or(() -> userRepository.findByUsername(usernameOrEmail))
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        Employee employee = employeeRepository.findByUser_Id(user.getId()).orElse(null);

        return new CustomUserPrincipal(
                user.getId(),
                employee != null ? employee.getId() : null,
                user.getUsername(),
                user.getPassword(),
                user.getRole(),
                user.getCompanyRoleId(),
                user.getCompanyRole(),
                user.getCompanyId()
        );
    }
}
