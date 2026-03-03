package com.erp.repo.hrsettings;

import com.erp.domain.hrsettings.JobCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobCodeRepository extends JpaRepository<JobCode, Long> {

    Optional<JobCode> findByCode(String code);

    List<JobCode> findByActiveTrue();
}