package com.erp.repo;

import com.erp.domain.EmployeeCurrentJob;
import com.erp.domain.EmployeeStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface EmployeeCurrentJobRepo extends JpaRepository<EmployeeCurrentJob, Long> {

    // ✅ Loads employee, jobCode, department in ONE query — no lazy load issues
    @EntityGraph(attributePaths = {"employee", "jobCode", "department"})
    Optional<EmployeeCurrentJob> findByEmployee_Id(Long employeeId);

    boolean existsByEmployee_Id(Long employeeId);
    boolean existsByDepartment_Id(Long departmentId);
    boolean existsByDivision_Id(Long divisionId);
    boolean existsByJobCode_Id(Long jobCodeId);

    /**
     * Is this job code the current job of some OTHER employee who is still on the
     * roster (status not in the "exited" set)? Used to keep a job code assignable
     * to only one active employee at a time — it frees up once the holder exits
     * (terminated / resigned / retired).
     */
    @Query("""
            SELECT COUNT(cj) > 0 FROM EmployeeCurrentJob cj
            WHERE cj.jobCode.id = :jobCodeId
              AND cj.employee.id <> :employeeId
              AND cj.employee.status NOT IN :freedStatuses
            """)
    boolean isJobCodeHeldByAnotherActiveEmployee(
            @Param("jobCodeId") Long jobCodeId,
            @Param("employeeId") Long employeeId,
            @Param("freedStatuses") Collection<EmployeeStatus> freedStatuses);

    /** The employee currently holding this job code, if any (for a helpful error message). */
    @EntityGraph(attributePaths = {"employee"})
    Optional<EmployeeCurrentJob> findFirstByJobCode_IdAndEmployee_IdNot(Long jobCodeId, Long employeeId);

    /** Job-code ids reserved by still-employed staff other than the given employee. */
    @Query("""
            SELECT cj.jobCode.id FROM EmployeeCurrentJob cj
            WHERE cj.employee.id <> :employeeId
              AND cj.employee.status NOT IN :freedStatuses
            """)
    java.util.List<Long> findJobCodeIdsHeldByActiveEmployeesExcluding(
            @Param("employeeId") Long employeeId,
            @Param("freedStatuses") Collection<EmployeeStatus> freedStatuses);

    /** Current-job rows (with employee + job code) held by still-employed staff other than the given employee. */
    @EntityGraph(attributePaths = {"employee", "jobCode"})
    @Query("""
            SELECT cj FROM EmployeeCurrentJob cj
            WHERE cj.employee.id <> :employeeId
              AND cj.employee.status NOT IN :freedStatuses
            """)
    java.util.List<EmployeeCurrentJob> findActiveHoldersExcluding(
            @Param("employeeId") Long employeeId,
            @Param("freedStatuses") Collection<EmployeeStatus> freedStatuses);
}