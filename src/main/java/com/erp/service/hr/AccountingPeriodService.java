package com.erp.service.hr;

import com.erp.domain.hr.AccountingPeriod;
import com.erp.domain.hr.Company;
import com.erp.domain.hr.PeriodStatus;
import com.erp.dto.hr.AccountingPeriodRequestDTO;
import com.erp.dto.hr.AccountingPeriodResponseDTO;
import com.erp.repo.hr.AccountingPeriodRepository;
import com.erp.repo.hr.CompanyRepository;
import com.erp.security.context.AuthContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AccountingPeriodService {

    private final AccountingPeriodRepository periodRepository;
    private final CompanyRepository companyRepository;
    private final AuthContext authContext;

    public AccountingPeriodResponseDTO createPeriod(AccountingPeriodRequestDTO request) {
        Long companyId = authContext.getCurrentCompanyId();

        boolean openExists = periodRepository
                .findByCompanyIdAndStatus(companyId, PeriodStatus.OPEN) != null;

        if (openExists) {
            throw new RuntimeException("An open accounting period already exists");
        }

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company not found"));

        boolean overlapping = periodRepository
                .existsByCompanyIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        companyId,
                        request.getEndDate(),
                        request.getStartDate()
                );

        if (overlapping) {
            throw new RuntimeException("Accounting period overlaps existing period");
        }

        AccountingPeriod period = AccountingPeriod.builder()
                .company(company)
                .periodName(request.getPeriodName())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .status(PeriodStatus.OPEN)
                .createdAt(LocalDate.now())
                .build();

        periodRepository.save(period);

        return mapToDTO(period);
    }

    public List<AccountingPeriodResponseDTO> getByCompany() {
        Long companyId = authContext.getCurrentCompanyId();
        return periodRepository.findByCompanyIdOrderByCreatedAtDesc(companyId)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public void closePeriod(Long periodId) {
        AccountingPeriod period = periodRepository.findById(periodId)
                .orElseThrow(() -> new RuntimeException("Period not found"));
        assertSameTenant(period);

        period.setStatus(PeriodStatus.CLOSED);
        periodRepository.save(period);
    }

    public void reopenPeriod(Long periodId) {
        AccountingPeriod period = periodRepository.findById(periodId)
                .orElseThrow(() -> new RuntimeException("Period not found"));
        assertSameTenant(period);

        Long companyId = period.getCompany().getId();

        boolean anotherOpenPeriodExists =
                periodRepository.findByCompanyIdAndStatus(
                        companyId,
                        PeriodStatus.OPEN
                ).getStatus() != null;

        if (anotherOpenPeriodExists) {
            throw new RuntimeException("Another accounting period is already open");
        }

        period.setStatus(PeriodStatus.OPEN);
        periodRepository.save(period);
    }

    public void validatePeriodOpen(Long companyId, LocalDate transactionDate) {

        AccountingPeriod period = periodRepository
                .findByCompanyIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        companyId,
                        transactionDate,
                        transactionDate
                )
                .orElseThrow(() -> new RuntimeException("No accounting period defined"));

        if (period.getStatus() == PeriodStatus.CLOSED) {
            throw new RuntimeException("Accounting period is closed");
        }
    }

    public AccountingPeriodResponseDTO getOpenPeriodStatus() {
        Long companyId = authContext.getCurrentCompanyId();

        AccountingPeriod acc = periodRepository
                .findByCompanyIdAndStatus(companyId, PeriodStatus.OPEN);

        return acc == null ? null : mapToDTO(acc);
    }

    /* ================= TENANT GUARD ================= */

    private void assertSameTenant(AccountingPeriod period) {
        if ("SUPER_ADMIN".equalsIgnoreCase(authContext.getCurrentUserRole())) return;
        Long currentCompanyId = authContext.getCurrentCompanyId();
        Long periodCompanyId = period != null && period.getCompany() != null
                ? period.getCompany().getId() : null;
        if (currentCompanyId == null || periodCompanyId == null
                || !currentCompanyId.equals(periodCompanyId)) {
            throw new AccessDeniedException("This accounting period belongs to a different company");
        }
    }

    private AccountingPeriodResponseDTO mapToDTO(AccountingPeriod period) {
        return AccountingPeriodResponseDTO.builder()
                .id(period.getId())
                .periodName(period.getPeriodName())
                .startDate(period.getStartDate())
                .endDate(period.getEndDate())
                .status(period.getStatus().name())
                .build();
    }
}