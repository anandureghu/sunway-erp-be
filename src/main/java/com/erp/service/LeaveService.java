// src/main/java/com/hrmodule/service/LeaveService.java
package com.erp.service;

import com.erp.domain.Employee;
import com.erp.domain.Leave;
import com.erp.dto.leave.LeaveRequest;
import com.erp.dto.leave.LeaveResponse;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.LeaveRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LeaveService {

    private final LeaveRepository leaveRepo;
    private final EmployeeRepository empRepo;

    public List<LeaveResponse> listForEmployee(Long employeeId) {
        return leaveRepo.findByEmployeeIdOrderByStartDateDesc(employeeId)
                .stream().map(LeaveResponse::from).toList();
    }

    public Integer approvedDays(Long employeeId) {
        return leaveRepo.sumApprovedDaysForEmployee(employeeId);
    }

    @Transactional
    public LeaveResponse create(LeaveRequest req) {
        Employee emp = empRepo.findById(req.getEmployeeId()).orElseThrow();

        Leave l = new Leave();
        l.setEmployee(emp);
        l.setLeaveType(nullToEmpty(req.getLeaveType()));
        l.setLeaveStatus(nullToEmpty(req.getLeaveStatus()));
        l.setStartDate(req.getStartDate());
        l.setEndDate(req.getEndDate());
        l.setDateReported(req.getDateReported());

        int businessDays = businessDaysInclusive(req.getStartDate(), req.getEndDate());
        l.setTotalDaysOnVacation(businessDays);

        // simple leave code: "L" + (count + 1)
        int count = leaveRepo.findByEmployeeIdOrderByStartDateDesc(emp.getId()).size();
        l.setLeaveCode(String.format("L%03d", count + 1));

        // snapshot balance if you receive one; otherwise leave null
        l.setLeaveBalance(req.getLeaveBalance());

        // You can add a validation here (if you track max balance):
        // int approvedSoFar = approvedDays(emp.getId());
        // int maxAnnual = 30; // put your rule
        // if ("Approved".equalsIgnoreCase(l.getLeaveStatus()) &&
        //     approvedSoFar + businessDays > maxAnnual) { throw new IllegalArgumentException("Insufficient balance"); }

        return LeaveResponse.from(leaveRepo.save(l));
    }

    @Transactional
    public LeaveResponse update(Long id, LeaveRequest req) {
        Leave l = leaveRepo.findById(id).orElseThrow();
        l.setLeaveType(nullToEmpty(req.getLeaveType()));
        l.setLeaveStatus(nullToEmpty(req.getLeaveStatus()));
        l.setStartDate(req.getStartDate());
        l.setEndDate(req.getEndDate());
        l.setDateReported(req.getDateReported());
        l.setLeaveBalance(req.getLeaveBalance());

        int businessDays = businessDaysInclusive(req.getStartDate(), req.getEndDate());
        l.setTotalDaysOnVacation(businessDays);

        return LeaveResponse.from(leaveRepo.save(l));
    }

    private int businessDaysInclusive(LocalDate start, LocalDate end) {
        if (start == null || end == null || end.isBefore(start)) return 0;
        int days = 0;
        LocalDate d = start;
        while (!d.isAfter(end)) {
            DayOfWeek dow = d.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) days++;
            d = d.plusDays(1);
        }
        return days;
    }

    private String nullToEmpty(String s) { return s == null ? "" : s.trim(); }
}
