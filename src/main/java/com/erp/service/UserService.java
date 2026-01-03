package com.erp.service;

import com.erp.domain.Employee;
import com.erp.domain.User;
import com.erp.dto.hr.UserDetailsDTO;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepo;
    private final EmployeeRepository employeeRepo;

    public UserService(UserRepository userRepo, EmployeeRepository employeeRepo) {
        this.userRepo = userRepo;
        this.employeeRepo = employeeRepo;
    }

    public UserDetailsDTO getUserDetails(Long id) {
        User user = userRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Check if user is also an employee
        Employee emp = employeeRepo.findByUserId(id).orElse(null);

        return UserDetailsDTO.builder()
                .userId(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .username(user.getUsername())
                .role(user.getRole())

                .employeeId(emp != null ? emp.getId() : null)
                .employeeNo(String.valueOf(emp != null ? emp.getEmployeeNo() : null))
                .firstName(emp != null ? emp.getFirstName() : null)
                .lastName(emp != null ? emp.getLastName() : null)
                .phoneNo(emp != null ? emp.getPhoneNo() : null)

                .companyId(emp != null && emp.getCompany() != null ? emp.getCompany().getId() : null)
                .companyName(emp != null && emp.getCompany() != null ? emp.getCompany().getCompanyName() : null)

                .departmentId(emp != null && emp.getDepartment() != null ? emp.getDepartment().getId() : null)
                .departmentName(emp != null && emp.getDepartment() != null ? emp.getDepartment().getDepartmentName() : null)
                .build();
    }
}
