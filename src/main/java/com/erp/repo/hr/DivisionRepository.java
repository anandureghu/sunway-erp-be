package com.erp.repo.hr;

import com.erp.domain.hr.Division;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DivisionRepository extends JpaRepository<Division, Long> {
    List<Division> findAllByCompanyIdOrderByCreatedAtDesc(Long companyId);

    List<Division> findAllByDepartment_IdOrderByCreatedAtDesc(Long departmentId);

    boolean existsByDepartment_Id(Long departmentId);

    Optional<Division> findByNameIgnoreCaseAndCompany_Id(String name, Long companyId);

    boolean existsByCodeAndCompany_Id(String code, Long companyId);
}
