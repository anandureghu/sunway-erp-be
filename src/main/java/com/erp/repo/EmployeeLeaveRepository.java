package com.erp.repo;

import com.erp.domain.EmployeeLeave;
import com.erp.domain.LeaveStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EmployeeLeaveRepository extends JpaRepository<EmployeeLeave, Long> {

    List<EmployeeLeave> findByEmployeeIdOrderByDateReportedDesc(Long employeeId);

    Optional<EmployeeLeave> findByIdAndEmployeeId(Long id, Long employeeId);

    @Query("""
        select l from EmployeeLeave l
        where l.employee.company.id = :companyId
          and l.leaveStatus = :leaveStatus
        order by l.dateReported desc
    """)
    List<EmployeeLeave> findByEmployeeCompany_IdAndLeaveStatusOrderByDateReportedDesc(
            @Param("companyId") Long companyId,
            @Param("leaveStatus") LeaveStatus leaveStatus
    );

    List<EmployeeLeave> findByEmployeeDepartmentIdAndLeaveStatusOrderByDateReportedDesc(
            Long departmentId,
            LeaveStatus leaveStatus
    );

    @Query("""
        select l from EmployeeLeave l
        where l.employee.id = :employeeId
          and l.leaveStatus = :status
          and l.startDate <= :periodEnd
          and l.endDate >= :periodStart
        order by l.dateReported desc
    """)
    List<EmployeeLeave> findLeavesForPayrollPeriod(
            @Param("employeeId") Long employeeId,
            @Param("status") LeaveStatus status,
            @Param("periodStart") LocalDate periodStart,
            @Param("periodEnd") LocalDate periodEnd
    );

    @Query("""
        select case when count(l) > 0 then true else false end
        from EmployeeLeave l
        where l.employee.id = :employeeId
          and l.leaveStatus = :status
          and l.startDate <= :periodEnd
          and l.endDate >= :periodStart
    """)
    boolean existsLeavesForPayrollPeriod(
            @Param("employeeId") Long employeeId,
            @Param("status") LeaveStatus status,
            @Param("periodStart") LocalDate periodStart,
            @Param("periodEnd") LocalDate periodEnd
    );

    default List<EmployeeLeave> findApprovedLeavesForPayrollPeriod(
            Long employeeId,
            LocalDate periodStart,
            LocalDate periodEnd
    ) {
        return findLeavesForPayrollPeriod(employeeId, LeaveStatus.APPROVED, periodStart, periodEnd);
    }

    default boolean existsPendingLeavesForPayrollPeriod(
            Long employeeId,
            LocalDate periodStart,
            LocalDate periodEnd
    ) {
        return existsLeavesForPayrollPeriod(employeeId, LeaveStatus.PENDING, periodStart, periodEnd);
    }
}
