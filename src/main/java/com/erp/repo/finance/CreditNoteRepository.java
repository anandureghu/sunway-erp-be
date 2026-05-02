package com.erp.repo.finance;


import com.erp.domain.finance.CreditNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CreditNoteRepository extends JpaRepository<CreditNote, Long> {
    List<CreditNote> findByCompanyIdOrderByCreatedAtDesc(Long companyId);
}
