package com.erp.service.hr;

import com.erp.domain.Employee;
import com.erp.domain.EmployeeStatus;
import com.erp.domain.hr.Company;
import com.erp.domain.hr.EmployeeExitInterview;
import com.erp.domain.security.AppModule;
import com.erp.dto.hr.ExitInterviewDTO;
import com.erp.dto.hr.ExitInterviewSummaryDTO;
import com.erp.repo.EmployeeCurrentJobRepo;
import com.erp.repo.EmployeeExitInterviewRepository;
import com.erp.repo.EmployeeRepository;
import com.erp.security.context.AuthContext;
import com.erp.security.guard.EmployeeAccessGuard;
import org.springframework.security.access.AccessDeniedException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Exit interviews for departing employees. Reading/writing an interview follows the
 * same tenant + EMPLOYEE_PROFILE access rules as the rest of the employee record;
 * an interview can only be saved once the employee is in an exit status.
 */
@Service
@RequiredArgsConstructor
public class ExitInterviewService {

    /** Statuses that make an employee eligible for an exit interview. */
    private static final Set<EmployeeStatus> EXIT_STATUSES =
            EnumSet.of(EmployeeStatus.RESIGNED, EmployeeStatus.TERMINATED, EmployeeStatus.RETIRED);

    private final EmployeeRepository employeeRepo;
    private final EmployeeExitInterviewRepository interviewRepo;
    private final EmployeeCurrentJobRepo currentJobRepo;
    private final EmployeeAccessGuard accessGuard;
    private final AuthContext authContext;
    private final ObjectMapper objectMapper;
    private final RetirementCompensationService retirementCompensationService;

    /** Company-wide list of exit / termination interviews for the HR Reports tab. */
    @Transactional(readOnly = true)
    public List<ExitInterviewSummaryDTO> listForCompany() {
        Long companyId = authContext.getCurrentCompanyId();
        if (companyId == null) {
            throw new AccessDeniedException("No company context for the current user");
        }
        return interviewRepo.findByCompany_IdOrderByUpdatedAtDesc(companyId).stream()
                .map(i -> {
                    Employee e = i.getEmployee();
                    return ExitInterviewSummaryDTO.builder()
                            .employeeId(e != null ? e.getId() : null)
                            .employeeNo(e != null ? e.getEmployeeNo() : null)
                            .employeeName(e != null ? fullName(e) : null)
                            .department(e != null && e.getDepartment() != null
                                    ? e.getDepartment().getDepartmentName() : null)
                            .designation(e != null ? resolveDesignation(e) : null)
                            .employeeStatus(e != null && e.getStatus() != null
                                    ? e.getStatus().name() : null)
                            .separationType(i.getSeparationType())
                            .lastWorkingDay(i.getLastWorkingDay())
                            .primaryReason(i.getPrimaryReason())
                            .status(i.getStatus())
                            .submittedAt(i.getSubmittedAt())
                            .updatedAt(i.getUpdatedAt())
                            .build();
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public ExitInterviewDTO get(Long employeeId) {
        Employee employee = employeeRepo.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));
        accessGuard.assertCanRead(employee, AppModule.EMPLOYEE_PROFILE);

        ExitInterviewDTO dto = new ExitInterviewDTO();
        applyEmployeeContext(dto, employee);

        interviewRepo.findByEmployee_Id(employeeId).ifPresent(i -> {
            dto.setExists(true);
            dto.setSeparationType(i.getSeparationType());
            dto.setLastWorkingDay(i.getLastWorkingDay());
            dto.setPrimaryReason(i.getPrimaryReason());
            dto.setStatus(i.getStatus());
            dto.setResponses(parse(i.getResponses()));
            dto.setSubmittedAt(i.getSubmittedAt());
            dto.setUpdatedAt(i.getUpdatedAt());
        });
        // Length of service runs to the recorded last working day (or today if not set).
        dto.setLengthOfService(
                computeLengthOfService(employee.getJoinDate(), dto.getLastWorkingDay()));
        return dto;
    }

    @Transactional
    public ExitInterviewDTO save(Long employeeId, ExitInterviewDTO dto) {
        Employee employee = employeeRepo.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));
        accessGuard.assertCanWrite(employee, AppModule.EMPLOYEE_PROFILE);

        if (!EXIT_STATUSES.contains(employee.getStatus())) {
            throw new IllegalStateException(
                    "An exit interview can only be recorded for an employee who has resigned, "
                            + "been terminated, or retired.");
        }

        EmployeeExitInterview interview = interviewRepo.findByEmployee_Id(employeeId)
                .orElseGet(() -> {
                    EmployeeExitInterview i = new EmployeeExitInterview();
                    i.setEmployee(employee);
                    i.setCompany(employee.getCompany());
                    return i;
                });

        interview.setSeparationType(dto.getSeparationType());
        interview.setLastWorkingDay(dto.getLastWorkingDay());
        interview.setPrimaryReason(dto.getPrimaryReason());
        interview.setResponses(write(dto.getResponses()));

        String status = dto.getStatus() != null ? dto.getStatus().toUpperCase() : "DRAFT";
        if (!"SUBMITTED".equals(status)) {
            status = "DRAFT";
        }
        boolean nowSubmitted = "SUBMITTED".equals(status) && !"SUBMITTED".equals(interview.getStatus());
        interview.setStatus(status);
        if (nowSubmitted) {
            interview.setSubmittedAt(Instant.now());
        }

        interviewRepo.save(interview);
        return get(employeeId);
    }

    private void applyEmployeeContext(ExitInterviewDTO dto, Employee employee) {
        dto.setEmployeeId(employee.getId());
        dto.setEmployeeNo(employee.getEmployeeNo());
        dto.setEmployeeName(fullName(employee));
        dto.setDepartment(employee.getDepartment() != null
                ? employee.getDepartment().getDepartmentName() : null);
        dto.setDateOfJoining(employee.getJoinDate());
        dto.setNationality(employee.getNationality());
        dto.setEmployeeStatus(employee.getStatus() != null ? employee.getStatus().name() : null);
        dto.setDesignation(resolveDesignation(employee));

        // Reporting manager = the employee's department head.
        if (employee.getDepartment() != null && employee.getDepartment().getManager() != null) {
            dto.setReportingManager(fullName(employee.getDepartment().getManager()));
        }
        // Work location = the company's location.
        Company company = employee.getCompany();
        if (company != null) {
            String loc = Stream.of(company.getCity(), company.getState(), company.getCountry())
                    .filter(s -> s != null && !s.isBlank())
                    .collect(Collectors.joining(", "));
            dto.setWorkLocation(loc.isBlank() ? null : loc);
        }
        // Accrued end-of-service benefit (gratuity).
        try {
            var eos = retirementCompensationService.computeAccruedAmount(employee);
            dto.setEosbAmount(eos != null ? eos.doubleValue() : null);
        } catch (Exception ignored) {
            // retirement compensation not configured — leave blank
        }
        dto.setHrRepresentatives(resolveHrRepresentatives(company));
    }

    /** HR-department employees (active) for the HR-representative signature picker. */
    private List<ExitInterviewDTO.HrRep> resolveHrRepresentatives(Company company) {
        if (company == null) {
            return List.of();
        }
        return employeeRepo
                .findByCompany_IdAndArchivedFalseOrderByCreatedAtDesc(company.getId())
                .stream()
                .filter(e -> e.getStatus() == null || !e.getStatus().isDepartedOrInactive())
                .filter(e -> {
                    var d = e.getDepartment();
                    if (d == null || d.getDepartmentName() == null) {
                        return false;
                    }
                    String n = d.getDepartmentName().toLowerCase();
                    return n.contains("hr") || n.contains("human resource");
                })
                .map(e -> new ExitInterviewDTO.HrRep(e.getId(), fullName(e)))
                .toList();
    }

    /** "X years Y months" (or days) between the join date and the given end date / today. */
    private String computeLengthOfService(LocalDate from, LocalDate to) {
        if (from == null) {
            return null;
        }
        LocalDate end = to != null ? to : LocalDate.now();
        if (end.isBefore(from)) {
            return null;
        }
        Period p = Period.between(from, end);
        if (p.getYears() == 0 && p.getMonths() == 0) {
            int days = p.getDays();
            return days + (days == 1 ? " day" : " days");
        }
        StringBuilder sb = new StringBuilder();
        if (p.getYears() > 0) {
            sb.append(p.getYears()).append(p.getYears() == 1 ? " year" : " years");
        }
        if (p.getMonths() > 0) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(p.getMonths()).append(p.getMonths() == 1 ? " month" : " months");
        }
        return sb.toString();
    }

    private String resolveDesignation(Employee employee) {
        try {
            var currentJob = currentJobRepo.findByEmployee_Id(employee.getId()).orElse(null);
            if (currentJob != null && currentJob.getJobCode() != null) {
                return currentJob.getJobCode().getTitle();
            }
        } catch (Exception ignored) {
            // no current job — leave blank
        }
        return null;
    }

    private String fullName(Employee e) {
        String f = e.getFirstName() == null ? "" : e.getFirstName();
        String l = e.getLastName() == null ? "" : e.getLastName();
        return (f + " " + l).trim();
    }

    private Map<String, Object> parse(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String write(Map<String, Object> responses) {
        if (responses == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(responses);
        } catch (Exception e) {
            throw new RuntimeException("Could not serialise exit-interview responses", e);
        }
    }
}
