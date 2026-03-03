package com.erp.repo.hr;

import com.erp.domain.hr.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, Long> {

    // ✅ Fetch departments by company
    List<Department> findAllByCompanyId(Long companyId);

    // ✅ Prevent duplicate department codes within same company
    boolean existsByDepartmentCodeAndCompanyId(String departmentCode, Long companyId);
}