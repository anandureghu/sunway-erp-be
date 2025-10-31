// src/main/java/com/hrmodule/repo/LeaveRepository.java
package com.hrmodule.repo;

import com.hrmodule.domain.Leave;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface LeaveRepository extends JpaRepository<Leave, Long> {

    List<Leave> findByEmployeeIdOrderByStartDateDesc(Long employeeId);

    @Query("""
     select coalesce(sum(l.totalDaysOnVacation),0)
     from Leave l
     where l.employee.id = :employeeId and l.leaveStatus = 'Approved'
  """)
    Integer sumApprovedDaysForEmployee(Long employeeId);
}
