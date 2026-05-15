package com.erp.repo;

import com.erp.domain.DocumentSequence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DocumentSequenceRepository extends JpaRepository<DocumentSequence, String> {
}
