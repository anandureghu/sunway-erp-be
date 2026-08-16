package com.erp.repo;

import com.erp.domain.hr.EmployeeExitInterview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmployeeExitInterviewRepository extends JpaRepository<EmployeeExitInterview, Long> {

    Optional<EmployeeExitInterview> findByEmployee_Id(Long employeeId);

    java.util.List<EmployeeExitInterview> findByCompany_IdOrderByUpdatedAtDesc(Long companyId);
}
