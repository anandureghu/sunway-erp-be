package com.erp.repo.hrsettings;

import com.erp.domain.hrsettings.JobCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobCodeRepository extends JpaRepository<JobCode, Long> {

    List<JobCode> findByCompany_Id(Long companyId);

    List<JobCode> findByCompany_IdAndActiveTrue(Long companyId);

    Optional<JobCode> findByCompany_IdAndCode(Long companyId, String code);

    Optional<JobCode> findByIdAndCompany_Id(Long id, Long companyId);
}