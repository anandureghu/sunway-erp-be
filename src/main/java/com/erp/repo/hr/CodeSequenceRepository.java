package com.erp.repo.hr;

import com.erp.domain.common.CodeSequence;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CodeSequenceRepository
        extends JpaRepository<CodeSequence, String> {
}