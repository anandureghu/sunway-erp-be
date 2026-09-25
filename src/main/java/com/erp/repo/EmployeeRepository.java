package com.erp.repo;

import com.erp.domain.Employee;
import com.erp.domain.EmployeeStatus;
import com.erp.domain.hr.Company;
import com.erp.domain.security.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    /**
     * Highest numeric employee number already used within a company (non-numeric
     * values such as "ADMIN" cast to 0 and are ignored). Used to seed the
     * per-company employee-number sequence. Returns 0 when the company has none.
     */
    @Query(value = "SELECT COALESCE(MAX(CASE WHEN employee_no REGEXP '^[0-9]+$' "
            + "THEN CAST(employee_no AS UNSIGNED) END), 0) "
            + "FROM employees WHERE company_id = :companyId", nativeQuery = true)
    long findMaxNumericEmployeeNo(@Param("companyId") Long companyId);

    List<Employee> findByCompany_IdOrderByCreatedAtDesc(Long companyId);

    /**
     * Duplicate-check candidates: same company (archived included) whose first AND
     * last names match case-insensitively. The middle name is compared in Java so
     * "Rashid Mubarak Al Naimi" and "Rashid Abdullah Al Naimi" stay distinct.
     */
    @Query("""
            SELECT e FROM Employee e
            WHERE e.company.id = :companyId
              AND LOWER(TRIM(e.firstName)) = :firstName
              AND LOWER(TRIM(e.lastName)) = :lastName
            """)
    List<Employee> findNameMatchCandidates(
            @Param("companyId") Long companyId,
            @Param("firstName") String firstName,
            @Param("lastName") String lastName);

    /** Employees in the company (archived included) carrying this identification / QID. */
    @Query("""
            SELECT e FROM Employee e
            WHERE e.company.id = :companyId
              AND e.identification IS NOT NULL
              AND LOWER(TRIM(e.identification)) = :identification
            """)
    List<Employee> findByCompanyAndIdentification(
            @Param("companyId") Long companyId,
            @Param("identification") String identification);

    /** Active (non-archived) employees — the working set shown across the app. */
    List<Employee> findByCompany_IdAndArchivedFalseOrderByCreatedAtDesc(Long companyId);

    Page<Employee> findByCompany_IdAndArchivedFalse(Long companyId, Pageable pageable);

    List<Employee> findByDepartment_IdAndArchivedFalseOrderByCreatedAtDesc(Long departmentId);

    List<Employee> findByCompany_IdAndStatusInAndArchivedFalseOrderByCreatedAtDesc(
            Long companyId, Collection<EmployeeStatus> statuses);

    @Query("""
            SELECT e FROM Employee e
            WHERE e.company.id = :companyId
              AND e.archived = false
              AND (e.status IS NULL OR e.status NOT IN :excludedStatuses)
            ORDER BY e.createdAt DESC
            """)
    List<Employee> findCurrentWorkforceByCompanyIdOrderByCreatedAtDesc(
            @Param("companyId") Long companyId,
            @Param("excludedStatuses") Collection<EmployeeStatus> excludedStatuses);

    @Query("""
            SELECT e FROM Employee e
            WHERE e.company.id = :companyId
              AND e.archived = false
              AND (e.status IS NULL OR e.status NOT IN :excludedStatuses)
            """)
    Page<Employee> findCurrentWorkforceByCompanyId(
            @Param("companyId") Long companyId,
            @Param("excludedStatuses") Collection<EmployeeStatus> excludedStatuses,
            Pageable pageable);

    @Query("""
            SELECT e FROM Employee e
            WHERE e.department.id = :departmentId
              AND e.archived = false
              AND (e.status IS NULL OR e.status NOT IN :excludedStatuses)
            ORDER BY e.createdAt DESC
            """)
    List<Employee> findCurrentWorkforceByDepartmentIdOrderByCreatedAtDesc(
            @Param("departmentId") Long departmentId,
            @Param("excludedStatuses") Collection<EmployeeStatus> excludedStatuses);

    List<Employee> findByCompany_IdAndStatusAndArchivedFalseOrderByCreatedAtDesc(
            Long companyId, EmployeeStatus status);

    /** Archived (former) employees — the records-only list. */
    List<Employee> findByCompany_IdAndArchivedTrueOrderByArchivedAtDesc(Long companyId);

    /** Paged archived employees — the HR "Audit" history view. */
    Page<Employee> findByCompany_IdAndArchivedTrueOrderByArchivedAtDesc(Long companyId, Pageable pageable);

    Page<Employee> findByCompany_Id(Long companyId, Pageable pageable);

    List<Employee> findByCompanyOrderByCreatedAtDesc(Company company);

    Optional<Employee> findFirstByCompany_IdAndUserRoleIn(
            Long companyId,
            List<Role> roles
    );

    List<Employee> findAllByCompany_IdAndUserRoleInOrderByCreatedAtDesc(
            Long companyId,
            List<Role> roles
    );

    boolean existsByCompany_IdAndUserRole(
            Long companyId,
            Role role
    );

    List<Employee> findByCompany_IdAndUser_CompanyRoleRef_NameIgnoreCaseOrderByCreatedAtDesc(
            Long companyId,
            String roleName
    );

    List<Employee> findByDepartment_IdOrderByCreatedAtDesc(Long departmentId);

    Optional<Employee> findByCompany_IdAndEmployeeNo(Long companyId, String employeeNo);

    Optional<Employee> findByUser_Id(Long userId);

    List<Employee> findAllByUser_Id(Long userId);

    Optional<Employee> findByUser_IdAndCompany_Id(Long userId, Long companyId);

    boolean existsByUser_IdAndCompany_Id(Long userId, Long companyId);

    List<Employee> findByCompany_IdAndStatus(Long companyId, EmployeeStatus employeeStatus);

    List<Employee> findByCompany_IdAndStatusIn(
            Long companyId, Collection<EmployeeStatus> statuses);

    // ======================================================
    //  Dashboard aggregations
    // ======================================================

    long countByCompany_Id(Long companyId);

    /** Directory-aligned total: non-archived employees only. */
    long countByCompany_IdAndArchivedFalse(Long companyId);

    /**
     * Current headcount for the HR dashboard: non-archived employees excluding the given
     * status (INACTIVE — separation complete). Null-status rows are counted.
     */
    @Query("""
            SELECT COUNT(e) FROM Employee e
            WHERE e.company.id = :companyId
              AND e.archived = false
              AND (e.status IS NULL OR e.status <> :excluded)
            """)
    long countHeadcountExcluding(@Param("companyId") Long companyId,
                                 @Param("excluded") EmployeeStatus excluded);

    long countByCompany_IdAndStatus(Long companyId, EmployeeStatus employeeStatus);

    long countByCompany_IdAndStatusAndArchivedFalse(Long companyId, EmployeeStatus employeeStatus);

    long countByCompany_IdAndJoinDateBetween(Long companyId, LocalDate from, LocalDate to);

    long countByCompany_IdAndArchivedFalseAndJoinDateBetween(
            Long companyId, LocalDate from, LocalDate to);

    /** Best-effort "resigned this month" proxy: no dedicated resignation-date field exists yet. */
    long countByCompany_IdAndStatusAndUpdatedAtBetween(
            Long companyId, EmployeeStatus employeeStatus, Instant from, Instant to);

    /** Rows of (departmentId, departmentName, employeeCount) for the "employees by department" widget. */
    @Query("""
            SELECT e.department.id, e.department.departmentName, COUNT(e)
            FROM Employee e
            WHERE e.company.id = :companyId
              AND e.archived = false
              AND e.department IS NOT NULL
              AND (e.status IS NULL OR e.status <> com.erp.domain.EmployeeStatus.INACTIVE)
            GROUP BY e.department.id, e.department.departmentName
            ORDER BY COUNT(e) DESC
            """)
    List<Object[]> countByDepartment(@Param("companyId") Long companyId);
}
