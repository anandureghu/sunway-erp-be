package com.erp.repo;

import com.erp.domain.Employee;
import com.erp.domain.EmployeeLeaveBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeLeaveBalanceRepository extends JpaRepository<EmployeeLeaveBalance, Long> {

    /**
     * Find leave balance by employee and leave type
     * ✅ CRITICAL: This must work correctly for leave operations
     */
    Optional<EmployeeLeaveBalance> findByEmployeeAndLeaveType(
            Employee employee,
            String leaveType
    );

    /**
     * Alternative: Find by employee ID and leave type (in case employee object has lazy loading issues)
     */
    @Query("SELECT b FROM EmployeeLeaveBalance b WHERE b.employee.id = :employeeId AND b.leaveType = :leaveType")
    Optional<EmployeeLeaveBalance> findByEmployeeIdAndLeaveType(
            @Param("employeeId") Long employeeId,
            @Param("leaveType") String leaveType
    );

    /**
     * Get all leave balances for an employee
     */
    List<EmployeeLeaveBalance> findByEmployee(Employee employee);

    /**
     * Get all leave balances by employee ID
     */
    @Query("SELECT b FROM EmployeeLeaveBalance b WHERE b.employee.id = :employeeId")
    List<EmployeeLeaveBalance> findByEmployeeId(@Param("employeeId") Long employeeId);

    /**
     * Get all balances for a specific leave type across all employees
     */
    List<EmployeeLeaveBalance> findByLeaveType(String leaveType);

    /**
     * Check if balance exists for employee and leave type
     */
    @Query("SELECT CASE WHEN COUNT(b) > 0 THEN true ELSE false END FROM EmployeeLeaveBalance b WHERE b.employee.id = :employeeId AND b.leaveType = :leaveType")
    boolean existsByEmployeeIdAndLeaveType(
            @Param("employeeId") Long employeeId,
            @Param("leaveType") String leaveType
    );
}