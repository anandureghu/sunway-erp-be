package com.erp.repo.finance;

import com.erp.domain.finance.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByCompanyId(Long companyId);
//    List<Transaction> findByTransactionCode(String code);
//    List<Transaction> findByPosted(Boolean posted);
}
