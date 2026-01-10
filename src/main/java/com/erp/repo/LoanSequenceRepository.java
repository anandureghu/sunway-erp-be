package com.erp.repo;

import com.erp.domain.LoanSequence;
import com.erp.domain.LoanType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;  // <-- ADD THIS IMPORT

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface LoanSequenceRepository extends JpaRepository<LoanSequence, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ls FROM LoanSequence ls WHERE ls.loanType = :loanType")
    Optional<LoanSequence> findByLoanTypeForUpdate(@Param("loanType") LoanType loanType);  // <-- ADD @Param HERE

    Optional<LoanSequence> findByLoanType(LoanType loanType);
}
