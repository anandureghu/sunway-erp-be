package com.erp.repo;

import com.erp.domain.appraisal.EmployeeAppraisal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmployeeAppraisalRepository extends JpaRepository<EmployeeAppraisal, Long> {

    // employee + user are fetched eagerly to avoid N+1 when building the
    // response (which reads employee name, role and the goal list).
    @EntityGraph(attributePaths = {"employee", "employee.user", "goals", "config"})
    Page<EmployeeAppraisal> findByEmployeeId(Long employeeId, Pageable pageable);

    // Tenant-scoped variant: only this company's appraisals for the employee.
    @EntityGraph(attributePaths = {"employee", "employee.user", "goals", "config"})
    Page<EmployeeAppraisal> findByEmployeeIdAndEmployee_Company_Id(
            Long employeeId, Long companyId, Pageable pageable);

    // Tenant-scoped year view (replaces the unscoped findByYear, which leaked
    // every company's appraisals to any VIEW_ALL holder).
    @EntityGraph(attributePaths = {"employee", "employee.user", "goals", "config"})
    Page<EmployeeAppraisal> findByYearAndEmployee_Company_Id(
            Integer year, Long companyId, Pageable pageable);

    // Unscoped year view — SUPER_ADMIN only (cross-company).
    @EntityGraph(attributePaths = {"employee", "employee.user", "goals", "config"})
    Page<EmployeeAppraisal> findByYear(Integer year, Pageable pageable);

    @EntityGraph(attributePaths = {"employee", "employee.user", "goals", "config"})
    Optional<EmployeeAppraisal> findByIdAndEmployeeId(Long id, Long employeeId);

    // Per-cycle uniqueness: one appraisal per employee, per cycle, per month.
    boolean existsByEmployeeIdAndConfig_IdAndMonth(Long employeeId, Long configId, String month);

    // Legacy: month+year uniqueness check (kept for backward compatibility)
    boolean existsByEmployeeIdAndYearAndMonth(Long employeeId, Integer year, String month);

    // Keep old one for backward compat if used elsewhere
    boolean existsByEmployeeIdAndYear(Long employeeId, Integer year);
}