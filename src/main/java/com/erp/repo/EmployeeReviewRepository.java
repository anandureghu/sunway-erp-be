package com.erp.repo;

import com.erp.domain.EmployeeReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmployeeReviewRepository extends JpaRepository<EmployeeReview, Long> {
    List<EmployeeReview> findByEmployeeId(Long employeeId);
}
