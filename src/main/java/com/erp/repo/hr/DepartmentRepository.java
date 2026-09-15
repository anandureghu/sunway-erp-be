package com.erp.repo.hr;

import com.erp.domain.hr.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, Long> {

    List<Department> findAllByCompanyIdOrderByCreatedAtDesc(Long companyId);

    boolean existsByDepartmentCodeAndCompanyId(String departmentCode, Long companyId);

    Optional<Department> findByDepartmentNameIgnoreCaseAndCompany_Id(String departmentName, Long companyId);

    Optional<Department> findByDepartmentCodeIgnoreCaseAndCompany_Id(String departmentCode, Long companyId);
}