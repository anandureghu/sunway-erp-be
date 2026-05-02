package com.erp.repo.finance;

import com.erp.domain.finance.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByCompanyIdOrderByCreatedAtDesc(Long companyId);
    List<Transaction> findByPaymentIdOrderByCreatedAtDesc(String paymentId);
    List<Transaction> findByPaymentIdAndTransactionTypeOrderByCreatedAtDesc(String paymentId, String transactionType);
    boolean existsByPaymentId(String paymentId);
    boolean existsByRelatedIdAndTransactionType(Long relatedId, String transactionType);

    boolean existsByRelatedSubIdAndTransactionType(Long relatedSubId, String transactionType);
//    List<Transaction> findByTransactionCode(String code);
//    List<Transaction> findByPosted(Boolean posted);
}
