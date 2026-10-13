package com.erp.repo.finance;

import com.erp.domain.finance.JournalEntry;
import com.erp.domain.finance.JournalEntryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Query("""
            SELECT j FROM JournalEntry j
            WHERE j.company.id = :companyId
              AND j.archived = :archived
              AND (
                   :q IS NULL OR :q = ''
                   OR LOWER(COALESCE(j.jeNumber, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(COALESCE(j.description, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(COALESCE(j.source, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(CAST(j.status AS string)) LIKE LOWER(CONCAT('%', :q, '%'))
              )
            """)
    Page<JournalEntry> searchByCompanyIdAndArchived(
            @Param("companyId") Long companyId,
            @Param("archived") Boolean archived,
            @Param("q") String q,
            Pageable pageable);
}