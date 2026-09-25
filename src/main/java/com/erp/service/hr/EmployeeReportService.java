package com.erp.service.hr;

import com.erp.domain.Employee;
import com.erp.domain.EmployeeAddress;
import com.erp.domain.EmployeeContactInfo;
import com.erp.domain.EmployeeCurrentJob;
import com.erp.domain.hr.Contract;
import com.erp.domain.salary.EmployeeCompensation;
import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.dto.hr.report.EmployeeReportRowDTO;
import com.erp.dto.hr.report.EmployeeSummaryReportDTO;
import com.erp.exception.NotFoundException;
import com.erp.repo.EmployeeCurrentJobRepo;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.contact.EmployeeAddressRepository;
import com.erp.repo.contact.EmployeeContactInfoRepository;
import com.erp.repo.hr.ContractRepository;
import com.erp.repo.salary.EmployeeCompensationRepository;
import com.erp.security.context.AuthContext;
import com.erp.service.security.PermissionCheckService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * HR Reports built from live data:
 * <ul>
 *   <li><b>Employee register</b> — every (non-archived) employee with status, nationality,
 *       department / division, designation, grade, join date, years of service,
 *       contract type and gross salary.</li>
 *   <li><b>Employee summary</b> — one employee's personal, contact, current-job,
 *       contract and salary details on a single sheet.</li>
 * </ul>
 * Access needs the HR_REPORTS or HRR_WORKFORCE view grant. Salary figures are only
 * returned to callers who may see salaries (SALARY or PAYROLL view-all).
 */
@Service
@RequiredArgsConstructor
public class EmployeeReportService {

    private final EmployeeRepository employeeRepo;
    private final EmployeeCurrentJobRepo currentJobRepo;
    private final ContractRepository contractRepo;
    private final EmployeeCompensationRepository compensationRepo;
    private final EmployeeContactInfoRepository contactInfoRepo;
    private final EmployeeAddressRepository addressRepo;
    private final PermissionCheckService permissionCheck;
    private final AuthContext authContext;

    // ------------------------------------------------------------------
    // Register (all employees)
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<EmployeeReportRowDTO> register() {
        Long companyId = requireAccess();
        boolean showSalary = canSeeSalary();

        List<Employee> employees = employeeRepo.findByCompany_IdAndArchivedFalseOrderByCreatedAtDesc(companyId);

        Map<Long, EmployeeCurrentJob> jobs = new HashMap<>();
        for (EmployeeCurrentJob j : currentJobRepo.findAllForCompanyReport(companyId)) {
            if (j.getEmployee() != null) {
                jobs.putIfAbsent(j.getEmployee().getId(), j);
            }
        }
        Map<Long, Contract> contracts = new HashMap<>();
        for (Contract c : contractRepo.findByCompany_IdAndDeletedFalseOrderByCreatedAtDesc(companyId)) {
            if (c.getEmployee() != null) {
                contracts.putIfAbsent(c.getEmployee().getId(), c); // newest first
            }
        }
        Map<Long, EmployeeCompensation> pay = showSalary
                ? activeCompensationByEmployee(companyId)
                : Map.of();

        return employees.stream()
                .map(e -> toRow(e, jobs.get(e.getId()), contracts.get(e.getId()),
                        showSalary ? pay.get(e.getId()) : null))
                .sorted(Comparator
                        .comparing((EmployeeReportRowDTO r) -> nz(r.getDepartmentName()), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(r -> nz(r.getFullName()), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    // ------------------------------------------------------------------
    // Single employee summary
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public EmployeeSummaryReportDTO summary(Long employeeId) {
        Long companyId = requireAccess();
        boolean showSalary = canSeeSalary();

        Employee e = employeeRepo.findById(employeeId)
                .orElseThrow(() -> new NotFoundException("Employee not found"));
        if (e.getCompany() == null || !Objects.equals(e.getCompany().getId(), companyId)) {
            throw new NotFoundException("Employee not found");
        }

        EmployeeCurrentJob job = currentJobRepo.findByEmployee_Id(e.getId()).orElse(null);
        Contract contract = contractRepo.findFirstByEmployeeIdAndDeletedFalseOrderByCreatedAtDesc(e.getId())
                .orElse(null);
        EmployeeContactInfo contact = contactInfoRepo.findByEmployeeId(e.getId()).orElse(null);
        List<EmployeeAddress> addresses = addressRepo.findByEmployeeId(e.getId());
        EmployeeCompensation comp = showSalary
                ? compensationRepo.findActiveByEmployee(e).orElse(null)
                : null;

        LocalDate joinDate = resolveJoinDate(e, job);
        Period service = serviceBetween(joinDate);

        String email = contact != null && notBlank(contact.getEmail()) ? contact.getEmail()
                : (e.getUser() != null ? e.getUser().getEmail() : null);

        return EmployeeSummaryReportDTO.builder()
                .id(e.getId())
                .employeeNo(e.getEmployeeNo())
                .prefix(e.getPrefix())
                .firstName(e.getFirstName())
                .middleName(e.getMiddleName())
                .lastName(e.getLastName())
                .fullName(fullName(e))
                .status(e.getStatus() != null ? e.getStatus().name() : null)
                .gender(e.getGender())
                .dateOfBirth(e.getDateOfBirth())
                .age(e.getDateOfBirth() != null ? Period.between(e.getDateOfBirth(), LocalDate.now()).getYears() : null)
                .maritalStatus(e.getMaritalStatus())
                .nationality(e.getNationality())
                .religion(e.getReligion())
                .identification(e.getIdentification())
                .birthplace(e.getBirthplace())
                .hometown(e.getHometown())
                .email(email)
                .phone(contact != null ? contact.getPhone() : null)
                .altPhone(contact != null ? contact.getAltPhone() : null)
                .addresses(addresses.stream()
                        .sorted(Comparator.comparing((EmployeeAddress a) -> !Boolean.TRUE.equals(a.getPrimaryAddress())))
                        .map(a -> EmployeeSummaryReportDTO.Address.builder()
                                .type(a.getAddressType())
                                .primary(Boolean.TRUE.equals(a.getPrimaryAddress()))
                                .text(Stream.of(a.getLine1(), a.getLine2(), a.getCity(), a.getState(),
                                                a.getPostalCode(), a.getCountry())
                                        .filter(EmployeeReportService::notBlank)
                                        .map(String::trim)
                                        .collect(Collectors.joining(", ")))
                                .build())
                        .filter(a -> notBlank(a.getText()))
                        .toList())
                .departmentName(departmentName(e, job))
                .divisionName(divisionName(job))
                .designation(job != null && job.getJobCode() != null ? job.getJobCode().getTitle() : null)
                .jobCode(job != null && job.getJobCode() != null ? job.getJobCode().getCode() : null)
                .gradeCode(job != null && job.getJobCode() != null ? job.getJobCode().getSalaryGrade() : null)
                .employmentType(job != null && job.getEmploymentType() != null ? job.getEmploymentType().name() : null)
                .employmentCategory(job != null && job.getEmploymentCategory() != null ? job.getEmploymentCategory().name() : null)
                .workLocation(job != null ? Stream.of(job.getWorkLocation(), job.getWorkCity(), job.getWorkCountry())
                        .filter(EmployeeReportService::notBlank)
                        .collect(Collectors.joining(" · ")) : null)
                .reportingManager(job != null && job.getReportingManager() != null ? fullName(job.getReportingManager()) : null)
                .companyRole(e.getCompanyRoleRef() != null ? e.getCompanyRoleRef().getName() : null)
                .joinDate(joinDate)
                .probationEndDate(e.getProbationEndDate())
                .expectedEndDate(job != null ? job.getExpectedEndDate() : null)
                .yearsOfService(years(service))
                .serviceLabel(serviceLabel(service))
                .contractCode(contract != null ? contract.getContractCode() : null)
                .contractType(contract != null && contract.getContractType() != null ? contract.getContractType().name() : null)
                .contractStatus(contract != null && contract.getStatus() != null ? contract.getStatus().name() : null)
                .contractStartDate(contract != null ? contract.getEffectiveDate()
                        : (job != null ? job.getContractStartDate() : null))
                .contractEndDate(contract != null ? contract.getExpirationDate()
                        : (job != null ? job.getContractEndDate() : null))
                .noticePeriodDays(contract != null ? contract.getNoticePeriodDays() : null)
                .grossSalary(comp != null ? money(comp.getTotalCompensation()) : null)
                .salaryVisible(showSalary)
                .currencyCode(currencyCode(e))
                .companyName(e.getCompany() != null ? e.getCompany().getCompanyName() : null)
                .build();
    }

    /** Whether the current caller may see salary figures (used by the UI to label the column). */
    public boolean salaryVisible() {
        return canSeeSalary();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private EmployeeReportRowDTO toRow(Employee e, EmployeeCurrentJob job, Contract contract,
                                       EmployeeCompensation comp) {
        LocalDate joinDate = resolveJoinDate(e, job);
        Period service = serviceBetween(joinDate);
        return EmployeeReportRowDTO.builder()
                .id(e.getId())
                .employeeNo(e.getEmployeeNo())
                .firstName(e.getFirstName())
                .middleName(e.getMiddleName())
                .lastName(e.getLastName())
                .fullName(fullName(e))
                .status(e.getStatus() != null ? e.getStatus().name() : null)
                .nationality(e.getNationality())
                .departmentName(departmentName(e, job))
                .divisionName(divisionName(job))
                .designation(job != null && job.getJobCode() != null ? job.getJobCode().getTitle() : null)
                .jobCode(job != null && job.getJobCode() != null ? job.getJobCode().getCode() : null)
                .gradeCode(job != null && job.getJobCode() != null ? job.getJobCode().getSalaryGrade() : null)
                .joinDate(joinDate)
                .yearsOfService(years(service))
                .serviceLabel(serviceLabel(service))
                .contractType(contract != null && contract.getContractType() != null ? contract.getContractType().name() : null)
                .employmentCategory(job != null && job.getEmploymentCategory() != null ? job.getEmploymentCategory().name() : null)
                .grossSalary(comp != null ? money(comp.getTotalCompensation()) : null)
                .gender(e.getGender())
                .dateOfBirth(e.getDateOfBirth())
                .probationEndDate(e.getProbationEndDate())
                .expectedEndDate(job != null ? job.getExpectedEndDate() : null)
                .contractEndDate(contract != null && contract.getExpirationDate() != null
                        ? contract.getExpirationDate()
                        : (job != null ? job.getContractEndDate() : null))
                .build();
    }

    private Map<Long, EmployeeCompensation> activeCompensationByEmployee(Long companyId) {
        LocalDate today = LocalDate.now();
        Map<Long, EmployeeCompensation> map = new HashMap<>();
        for (EmployeeCompensation c : compensationRepo.findActiveByCompanyId(companyId)) {
            if (c.getEmployee() == null) continue;
            // Same rule as findActiveByEmployee: in effect today.
            if (c.getEffectiveFrom() != null && c.getEffectiveFrom().isAfter(today)) continue;
            if (c.getEffectiveTo() != null && c.getEffectiveTo().isBefore(today)) continue;
            map.merge(c.getEmployee().getId(), c, (a, b) ->
                    nzDate(a.getEffectiveFrom()).isAfter(nzDate(b.getEffectiveFrom())) ? a : b);
        }
        return map;
    }

    /** Company id for the caller, after checking the HR reports grant. */
    private Long requireAccess() {
        Long companyId = authContext.getCurrentCompanyId();
        if (companyId == null) {
            throw new AccessDeniedException("No company context for the current user");
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean allowed = permissionCheck.hasAny(auth, AppModule.HR_REPORTS, AppAction.VIEW_ALL, AppAction.VIEW_OWN)
                || permissionCheck.hasAny(auth, AppModule.HRR_WORKFORCE, AppAction.VIEW_ALL, AppAction.VIEW_OWN);
        if (!allowed) {
            throw new AccessDeniedException("HR reports permission is required to view employee reports");
        }
        return companyId;
    }

    private boolean canSeeSalary() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return permissionCheck.hasAny(auth, AppModule.SALARY, AppAction.VIEW_ALL)
                || permissionCheck.hasAny(auth, AppModule.PAYROLL, AppAction.VIEW_ALL);
    }

    private static LocalDate resolveJoinDate(Employee e, EmployeeCurrentJob job) {
        if (e.getJoinDate() != null) return e.getJoinDate();
        return job != null ? job.getStartDate() : null;
    }

    private static Period serviceBetween(LocalDate joinDate) {
        if (joinDate == null || joinDate.isAfter(LocalDate.now())) return null;
        return Period.between(joinDate, LocalDate.now());
    }

    private static Double years(Period p) {
        if (p == null) return null;
        double y = p.getYears() + p.getMonths() / 12.0;
        return Math.round(y * 10.0) / 10.0;
    }

    private static String serviceLabel(Period p) {
        if (p == null) return null;
        int y = p.getYears();
        int m = p.getMonths();
        if (y == 0 && m == 0) return p.getDays() + (p.getDays() == 1 ? " day" : " days");
        StringBuilder sb = new StringBuilder();
        if (y > 0) sb.append(y).append(y == 1 ? " year" : " years");
        if (m > 0) sb.append(sb.length() > 0 ? " " : "").append(m).append(m == 1 ? " month" : " months");
        return sb.toString();
    }

    private static String departmentName(Employee e, EmployeeCurrentJob job) {
        if (job != null && job.getDepartment() != null) return job.getDepartment().getDepartmentName();
        return e.getDepartment() != null ? e.getDepartment().getDepartmentName() : null;
    }

    private static String divisionName(EmployeeCurrentJob job) {
        if (job == null) return null;
        if (job.getDivision() != null) return job.getDivision().getName();
        return job.getJobCode() != null && job.getJobCode().getDivision() != null
                ? job.getJobCode().getDivision().getName() : null;
    }

    private static String fullName(Employee e) {
        return Stream.of(e.getFirstName(), e.getMiddleName(), e.getLastName())
                .filter(EmployeeReportService::notBlank)
                .map(String::trim)
                .collect(Collectors.joining(" "));
    }

    private static String currencyCode(Employee e) {
        try {
            return e.getCompany() != null && e.getCompany().getCurrency() != null
                    ? e.getCompany().getCurrency().getCurrencyCode() : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private static BigDecimal money(Double v) {
        return v == null ? null : BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static LocalDate nzDate(LocalDate d) {
        return d == null ? LocalDate.MIN : d;
    }
}
