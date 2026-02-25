package com.erp.repo.hr;

import com.erp.domain.hr.Division;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DivisionRepository extends JpaRepository<Division, Long> {
    List<Division> findAllByCompanyCreatedBy(String createdBy);

    List<Division> findAllByCompanyId(Long companyId);

    List<Division> findAllByDepartmentIdAndCompanyId(Long departmentId, Long companyId);
}
