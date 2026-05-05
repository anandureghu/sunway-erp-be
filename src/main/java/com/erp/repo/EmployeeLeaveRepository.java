package com.erp.repo;

import com.erp.domain.EmployeeLeave;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EmployeeLeaveRepository extends JpaRepository<EmployeeLeave, Long> {

    List<EmployeeLeave> findByEmployeeIdOrderByDateReportedDesc(Long employeeId);

    Optional<EmployeeLeave> findByIdAndEmployeeId(Long id, Long employeeId);

    List<EmployeeLeave> findByEmployeeCompany_IdAndLeaveStatusOrderByDateReportedDesc(
            Long companyId,
            String leaveStatus
    );

    List<EmployeeLeave> findByEmployeeDepartmentIdAndLeaveStatusOrderByDateReportedDesc(
            Long departmentId,
            String leaveStatus
    );

    List<EmployeeLeave> findByLeaveStatusOrderByDateReportedDesc(String leaveStatus);

    @Query("""
        select l from EmployeeLeave l
        where l.employee.id = :employeeId
          and upper(l.leaveStatus) = upper(:status)
          and l.startDate <= :periodEnd
          and l.endDate >= :periodStart
        order by l.dateReported desc
    """)
    List<EmployeeLeave> findLeavesForPayrollPeriod(
            @Param("employeeId") Long employeeId,
            @Param("status") String status,
            @Param("periodStart") LocalDate periodStart,
            @Param("periodEnd") LocalDate periodEnd
    );

    @Query("""
        select case when count(l) > 0 then true else false end
        from EmployeeLeave l
        where l.employee.id = :employeeId
          and upper(l.leaveStatus) = upper(:status)
          and l.startDate <= :periodEnd
          and l.endDate >= :periodStart
    """)
    boolean existsLeavesForPayrollPeriod(
            @Param("employeeId") Long employeeId,
            @Param("status") String status,
            @Param("periodStart") LocalDate periodStart,
            @Param("periodEnd") LocalDate periodEnd
    );

    @Query("""
        select l from EmployeeLeave l
        where l.employee.id = :employeeId
          and upper(l.leaveStatus) = 'APPROVED'
          and l.startDate <= :periodEnd
          and l.endDate >= :periodStart
        order by l.dateReported desc
    """)
    List<EmployeeLeave> findApprovedLeavesForPayrollPeriod(
            @Param("employeeId") Long employeeId,
            @Param("periodStart") LocalDate periodStart,
            @Param("periodEnd") LocalDate periodEnd
    );

    @Query("""
        select case when count(l) > 0 then true else false end
        from EmployeeLeave l
        where l.employee.id = :employeeId
          and upper(l.leaveStatus) = 'PENDING'
          and l.startDate <= :periodEnd
          and l.endDate >= :periodStart
    """)
    boolean existsPendingLeavesForPayrollPeriod(
            @Param("employeeId") Long employeeId,
            @Param("periodStart") LocalDate periodStart,
            @Param("periodEnd") LocalDate periodEnd
    );
}