package com.erp.repo.hr;

import com.erp.domain.hr.Contract;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ContractRepository extends JpaRepository<Contract, Long> {

    Optional<Contract> findByEmployeeId(Long employeeId);

    boolean existsByContractCode(String contractCode);
}