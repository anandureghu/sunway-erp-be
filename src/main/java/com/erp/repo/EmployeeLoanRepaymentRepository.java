package com.erp.repo;

import com.erp.domain.EmployeeLoanRepayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmployeeLoanRepaymentRepository extends JpaRepository<EmployeeLoanRepayment, Long> {

    List<EmployeeLoanRepayment> findByLoanIdOrderByPaymentDateAscIdAsc(Long loanId);
}
