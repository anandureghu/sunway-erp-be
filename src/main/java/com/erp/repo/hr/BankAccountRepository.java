package com.erp.repo.hr;

import com.erp.domain.hr.BankAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BankAccountRepository extends JpaRepository<BankAccount, Long> {

    List<BankAccount> findByCompanyId(Long companyId);

    boolean existsByCompanyIdAndPrimaryAccountTrue(Long companyId);
}
