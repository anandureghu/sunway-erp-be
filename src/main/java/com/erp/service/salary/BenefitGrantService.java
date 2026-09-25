package com.erp.service.salary;

import com.erp.domain.Employee;
import com.erp.domain.salary.BenefitGrantType;
import com.erp.domain.salary.EmployeeBenefitGrant;
import com.erp.dto.file.FileCategory;
import com.erp.dto.file.FileUploadResult;
import com.erp.dto.salary.BenefitGrantDTO;
import com.erp.dto.salary.BenefitGrantRequestDTO;
import com.erp.exception.ConflictException;
import com.erp.exception.NotFoundException;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.salary.EmployeeBenefitGrantRepository;
import com.erp.security.context.AuthContext;
import com.erp.service.file.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * HR Policies → Benefits: one-off grants (annual ticket, bonus, reimbursement) paid
 * through payroll. A grant is PENDING until the first payroll run whose period
 * reaches its pay month pays it; the run then marks it PAID.
 *
 * Rules: an employee gets at most one annual ticket per calendar year; a
 * reimbursement must carry a supporting document.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BenefitGrantService {

    private static final DateTimeFormatter MONTH_LABEL =
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);
    /** Pending grants from any earlier month are still paid by the next run. */
    private static final LocalDate EARLIEST_PAY_MONTH = LocalDate.of(2000, 1, 1);

    private final EmployeeBenefitGrantRepository grantRepo;
    private final EmployeeRepository employeeRepo;
    private final FileStorageService fileStorageService;
    private final AuthContext authContext;

    @Transactional(readOnly = true)
    public List<BenefitGrantDTO> list() {
        Long companyId = requireCompany();
        return grantRepo.findByCompanyIdOrderByPayMonthDescIdDesc(companyId).stream()
                .map(this::toDTO)
                .toList();
    }

    /**
     * The annual ticket already granted to the employee in the pay month's calendar
     * year, if any — the UI shows it as a warning before HR submits.
     */
    @Transactional(readOnly = true)
    public Optional<BenefitGrantDTO> findAnnualTicketInYear(Long employeeId, String payMonth) {
        Employee employee = getCompanyEmployee(employeeId);
        LocalDate month = parsePayMonth(payMonth);
        return existingTicketInYear(employee.getId(), month.getYear()).map(this::toDTO);
    }

    @Transactional
    public BenefitGrantDTO create(BenefitGrantRequestDTO dto, MultipartFile document) {
        if (dto == null) {
            throw new IllegalArgumentException("Benefit details are required.");
        }
        Employee employee = getCompanyEmployee(dto.getEmployeeId());
        BenefitGrantType type = parseType(dto.getBenefitType());
        LocalDate payMonth = parsePayMonth(dto.getPayMonth());

        BigDecimal amount = dto.getAmount();
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Enter an amount greater than zero.");
        }

        boolean hasDocument = document != null && !document.isEmpty();
        if (type == BenefitGrantType.REIMBURSEMENT && !hasDocument) {
            throw new IllegalArgumentException(
                    "Upload the supporting document (receipt or invoice) for a reimbursement.");
        }

        if (type == BenefitGrantType.ANNUAL_TICKET) {
            existingTicketInYear(employee.getId(), payMonth.getYear()).ifPresent(existing -> {
                throw new ConflictException(fullName(employee)
                        + " has already received the annual ticket for " + payMonth.getYear()
                        + " (" + existing.getPayMonth().format(MONTH_LABEL) + "). "
                        + "The annual ticket can be granted once a year only.");
            });
        }

        EmployeeBenefitGrant grant = new EmployeeBenefitGrant();
        grant.setCompanyId(employee.getCompanyId());
        grant.setEmployee(employee);
        grant.setBenefitType(type);
        grant.setAmount(amount.setScale(2, java.math.RoundingMode.HALF_UP));
        grant.setPayMonth(payMonth);
        grant.setDescription(clean(dto.getDescription()));
        grant.setStatus(EmployeeBenefitGrant.STATUS_PENDING);
        grant.setCreatedBy(safeUserId());
        grant = grantRepo.save(grant);

        if (hasDocument) {
            FileUploadResult upload = fileStorageService.upload(
                    document,
                    FileCategory.BENEFIT_REIMBURSEMENT_DOCUMENT,
                    grant.getId().toString(),
                    false,
                    grant.getCompanyId());
            grant.setDocumentPath(upload.getBlobPath());
            grant = grantRepo.save(grant);
        }
        return toDTO(grant);
    }

    /** Removes a grant that payroll has not paid yet (and its document). */
    @Transactional
    public void delete(Long id) {
        EmployeeBenefitGrant grant = getCompanyGrant(id);
        if (EmployeeBenefitGrant.STATUS_PAID.equals(grant.getStatus())) {
            throw new IllegalStateException(
                    "This benefit has already been paid in payroll and cannot be deleted.");
        }
        String documentPath = grant.getDocumentPath();
        grantRepo.delete(grant);
        if (documentPath != null) {
            try {
                fileStorageService.deleteByBlobPath(documentPath);
            } catch (Exception ex) {
                log.warn("Could not delete benefit document {}", documentPath, ex);
            }
        }
    }

    // ------------------------------------------------------------------
    // Payroll integration
    // ------------------------------------------------------------------

    /** Unpaid grants whose pay month is on or before the month the period ends in. */
    @Transactional(readOnly = true)
    public List<EmployeeBenefitGrant> pendingForPeriod(Long employeeId, LocalDate periodEnd) {
        if (employeeId == null || periodEnd == null) {
            return List.of();
        }
        return grantRepo.findByEmployee_IdAndStatusAndPayMonthBetween(
                employeeId,
                EmployeeBenefitGrant.STATUS_PENDING,
                EARLIEST_PAY_MONTH,
                periodEnd.withDayOfMonth(1));
    }

    /** Marks the grants a payroll run paid. */
    @Transactional
    public void markPaid(List<EmployeeBenefitGrant> grants, Long payrollId) {
        if (grants == null || grants.isEmpty()) {
            return;
        }
        for (EmployeeBenefitGrant grant : grants) {
            grant.setStatus(EmployeeBenefitGrant.STATUS_PAID);
            grant.setPayrollId(payrollId);
        }
        grantRepo.saveAll(grants);
    }

    @Transactional(readOnly = true)
    public List<EmployeeBenefitGrant> paidInPayroll(Long payrollId) {
        return payrollId == null ? List.of() : grantRepo.findByPayrollId(payrollId);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Optional<EmployeeBenefitGrant> existingTicketInYear(Long employeeId, int year) {
        return grantRepo.findByEmployee_IdAndBenefitTypeAndPayMonthBetween(
                        employeeId,
                        BenefitGrantType.ANNUAL_TICKET,
                        LocalDate.of(year, 1, 1),
                        LocalDate.of(year, 12, 31))
                .stream()
                .min(Comparator.comparing(EmployeeBenefitGrant::getPayMonth));
    }

    private Long requireCompany() {
        Long companyId = authContext.getCurrentCompanyId();
        if (companyId == null) {
            throw new IllegalStateException("No company context.");
        }
        return companyId;
    }

    private Employee getCompanyEmployee(Long employeeId) {
        if (employeeId == null) {
            throw new IllegalArgumentException("Select an employee.");
        }
        Long companyId = requireCompany();
        Employee employee = employeeRepo.findById(employeeId)
                .orElseThrow(() -> new NotFoundException("Employee not found"));
        if (!Objects.equals(employee.getCompanyId(), companyId)) {
            throw new NotFoundException("Employee not found");
        }
        return employee;
    }

    private EmployeeBenefitGrant getCompanyGrant(Long id) {
        Long companyId = requireCompany();
        EmployeeBenefitGrant grant = grantRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Benefit not found"));
        if (!Objects.equals(grant.getCompanyId(), companyId)) {
            throw new NotFoundException("Benefit not found");
        }
        return grant;
    }

    private BenefitGrantType parseType(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Select a benefit type.");
        }
        try {
            return BenefitGrantType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown benefit type: " + value);
        }
    }

    /** Accepts yyyy-MM or yyyy-MM-dd; returns the first day of that month. */
    private LocalDate parsePayMonth(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Select the pay month.");
        }
        String v = value.trim();
        try {
            return YearMonth.parse(v.length() >= 7 ? v.substring(0, 7) : v).atDay(1);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Pay month must be in the format YYYY-MM.");
        }
    }

    private Long safeUserId() {
        try {
            return authContext.getCurrentUserId();
        } catch (Exception ex) {
            return null;
        }
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String fullName(Employee e) {
        String name = ((e.getFirstName() == null ? "" : e.getFirstName()) + " "
                + (e.getLastName() == null ? "" : e.getLastName())).trim();
        return name.isEmpty() ? "This employee" : name;
    }

    private BenefitGrantDTO toDTO(EmployeeBenefitGrant g) {
        Employee e = g.getEmployee();
        String documentUrl = null;
        if (g.getDocumentPath() != null) {
            try {
                documentUrl = fileStorageService.getPrivateSasUrl(g.getDocumentPath());
            } catch (Exception ex) {
                log.warn("Could not sign benefit document URL {}", g.getDocumentPath(), ex);
            }
        }
        return BenefitGrantDTO.builder()
                .id(g.getId())
                .employeeId(e != null ? e.getId() : null)
                .employeeNo(e != null ? e.getEmployeeNo() : null)
                .employeeName(e != null ? fullName(e) : null)
                .benefitType(g.getBenefitType() != null ? g.getBenefitType().name() : null)
                .benefitTypeLabel(g.getBenefitType() != null ? g.getBenefitType().getLabel() : null)
                .amount(g.getAmount())
                .payMonth(g.getPayMonth())
                .description(g.getDescription())
                .documentUrl(documentUrl)
                .status(g.getStatus())
                .payrollId(g.getPayrollId())
                .createdAt(g.getCreatedAt())
                .build();
    }
}
