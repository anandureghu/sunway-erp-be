package com.erp.repo.hr;

import com.erp.domain.hr.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, Long> {
    List<Department> findAllByCompanyCreatedBy(String createdBy);

    List<Department> findAllByCompanyId(Long companyId);
}
