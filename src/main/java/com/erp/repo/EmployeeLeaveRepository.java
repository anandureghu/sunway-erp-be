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

    /** Employee ids that have an approved leave covering the given date. */
    @Query("""
        select distinct l.employee.id from EmployeeLeave l
        where l.leaveStatus = com.erp.domain.LeaveStatus.APPROVED
          and l.startDate <= :onDate
          and l.endDate >= :onDate
    """)
    List<Long> findEmployeeIdsOnApprovedLeave(@Param("onDate") LocalDate onDate);

    /** Overlap check that ignores one leave (the one being edited). */
    @Query("""
        select case when count(l) > 0 then true else false end
        from EmployeeLeave l
        where l.employee.id = :employeeId
          and l.id <> :excludeLeaveId
          and l.leaveStatus = :status
          and l.startDate <= :periodEnd
          and l.endDate >= :periodStart
    """)
    boolean existsOtherLeaveForPeriod(
            @Param("employeeId") Long employeeId,
            @Param("excludeLeaveId") Long excludeLeaveId,
            @Param("status") LeaveStatus status,
            @Param("periodStart") LocalDate periodStart,
            @Param("periodEnd") LocalDate periodEnd
    );

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

    /** All leaves in a company whose status is one of the given set (for reports). */
    @Query("""
        select l from EmployeeLeave l
        where l.employee.company.id = :companyId
          and l.leaveStatus in :statuses
        order by l.dateReported desc
    """)
    List<EmployeeLeave> findByCompanyAndStatuses(
            @Param("companyId") Long companyId,
            @Param("statuses") List<LeaveStatus> statuses
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

    // ======================================================
    //  Dashboard aggregations
    // ======================================================

    /** Employee ids (within a company) that have an approved leave covering the given date. */
    @Query("""
        select distinct l.employee.id from EmployeeLeave l
        where l.employee.company.id = :companyId
          and l.leaveStatus = com.erp.domain.LeaveStatus.APPROVED
          and l.startDate <= :onDate
          and l.endDate >= :onDate
    """)
    List<Long> findEmployeeIdsOnApprovedLeaveForCompany(
            @Param("companyId") Long companyId,
            @Param("onDate") LocalDate onDate);

    /** Leave requests submitted (dateReported) within a window, for the monthly leave-summary widget. */
    List<EmployeeLeave> findByEmployee_Company_IdAndDateReportedBetween(
            Long companyId, LocalDate from, LocalDate to);

    /**
     * Monthly count of leave requests by year + month over dateReported, scoped to company.
     * Returns rows of (year, month, count).
     */
    @Query("""
        select year(l.dateReported), month(l.dateReported), count(l)
        from EmployeeLeave l
        where l.employee.company.id = :companyId
          and l.dateReported between :from and :to
        group by year(l.dateReported), month(l.dateReported)
        order by year(l.dateReported), month(l.dateReported)
    """)
    List<Object[]> monthlyRequestCounts(
            @Param("companyId") Long companyId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
