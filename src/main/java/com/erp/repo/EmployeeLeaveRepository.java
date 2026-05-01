package com.erp.repo;

import com.erp.domain.EmployeeLeave;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmployeeLeaveRepository
        extends JpaRepository<EmployeeLeave, Long> {

    List<EmployeeLeave> findByEmployeeIdOrderByDateReportedDesc(Long employeeId);

    java.util.Optional<EmployeeLeave> findByIdAndEmployeeId(Long id, Long employeeId);

    List<EmployeeLeave> findByEmployeeCompany_IdAndLeaveStatusOrderByDateReportedDesc(Long companyId, String leaveStatus);

    List<EmployeeLeave> findByEmployeeDepartmentIdAndLeaveStatusOrderByDateReportedDesc(Long departmentId, String leaveStatus);

    List<EmployeeLeave> findByLeaveStatusOrderByDateReportedDesc(String leaveStatus);
}
