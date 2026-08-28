package com.erp.repo.hr;

import com.erp.domain.enums.ContractStatus;
import com.erp.domain.hr.Contract;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ContractRepository extends JpaRepository<Contract, Long> {

    Optional<Contract> findFirstByEmployeeIdAndDeletedFalseOrderByCreatedAtDesc(Long employeeId);

    boolean existsByContractCode(String contractCode);

    // ======================================================
    //  Dashboard aggregations
    // ======================================================

    /** Non-deleted contracts expiring on/before the cutoff, for the "contracts expiring" widgets. */
    @EntityGraph(attributePaths = "employee")
    List<Contract> findByCompany_IdAndDeletedFalseAndExpirationDateLessThanEqual(
            Long companyId, LocalDate cutoff);

    /** DRAFT contracts stand in for "pending renewal/approval" until a dedicated workflow exists. */
    long countByCompany_IdAndDeletedFalseAndStatus(Long companyId, ContractStatus status);

    @EntityGraph(attributePaths = "employee")
    List<Contract> findTop5ByCompany_IdAndDeletedFalseOrderByUpdatedAtDesc(Long companyId);

    /** Non-deleted contracts in the given statuses for HR renewal review, soonest-expiring first. */
    @EntityGraph(attributePaths = "employee")
    List<Contract> findByCompany_IdAndDeletedFalseAndStatusInOrderByExpirationDateAsc(
            Long companyId, Collection<ContractStatus> statuses);

    /** Lapsed contracts for the daily contract-end sweep, across all tenants. */
    @EntityGraph(attributePaths = "employee")
    List<Contract> findByDeletedFalseAndStatusAndExpirationDateBefore(
            ContractStatus status, LocalDate cutoff);
}