package com.erp.repo.finance;

import com.erp.domain.finance.JournalEntry;
import com.erp.domain.finance.JournalEntryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JournalEntryRepository
        extends JpaRepository<JournalEntry, Long> {

    Optional<JournalEntry> findByIdAndCompanyId(Long id, Long companyId);

    Page<JournalEntry> findAllByCompanyId(Long companyId, Pageable pageable);

    Page<JournalEntry> findAllByCompanyIdAndArchived(
            Long companyId, Boolean archived, Pageable pageable);

    List<JournalEntry> findAllByCompanyIdAndArchivedTrue(Long companyId);

    long countByCompanyIdAndArchivedFalseAndStatus(Long companyId, JournalEntryStatus status);
}