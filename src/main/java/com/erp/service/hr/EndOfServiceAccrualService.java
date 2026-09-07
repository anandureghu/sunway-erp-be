package com.erp.service.hr;

import com.erp.domain.Employee;
import com.erp.domain.EmployeeStatus;
import com.erp.domain.hr.Company;
import com.erp.dto.hr.ProcessAccountPair;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.hr.CompanyRepository;
import com.erp.service.finance.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Posts monthly End-of-Service Benefit accruals: debit EOSB expense, credit the
 * configured liability provision account for each payable employee.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EndOfServiceAccrualService {

    private static final Set<EmployeeStatus> ACCRUABLE_STATUSES = EnumSet.of(
            EmployeeStatus.ACTIVE,
            EmployeeStatus.ON_LEAVE,
            EmployeeStatus.UNDER_PROBATION);

    private final CompanyRepository companyRepository;
    private final EmployeeRepository employeeRepository;
    private final ProcessAccountDefaultsService processAccountDefaultsService;
    private final RetirementCompensationService retirementCompensationService;
    private final TransactionService transactionService;

    /**
     * Accrue EOSB for the calendar month containing {@code asOf} for every company
     * that has retirement compensation enabled and both EOSB accounts configured.
     */
    @Transactional
    public int accrueForMonth(LocalDate asOf) {
        YearMonth period = YearMonth.from(asOf);
        int yearMonth = period.getYear() * 100 + period.getMonthValue();
        LocalDate postingDate = period.atEndOfMonth();
        int posted = 0;

        for (Company company : companyRepository.findAll()) {
            if (!company.isRetirementCompensationEnabled()) {
                continue;
            }
            Optional<ProcessAccountPair> accounts =
                    processAccountDefaultsService.resolveEndOfServiceAccounts(company.getId());
            if (accounts.isEmpty()) {
                log.debug(
                        "Skipping EOSB accrual for company {} — debit/credit accounts not configured",
                        company.getId());
                continue;
            }
            ProcessAccountPair pair = accounts.get();
            List<Employee> employees = employeeRepository.findByCompany_IdAndStatusIn(
                    company.getId(), ACCRUABLE_STATUSES);
            for (Employee employee : employees) {
                BigDecimal amount = retirementCompensationService.computeMonthlyAccrualAmount(employee);
                if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                String label = employeeLabel(employee);
                transactionService.recordEndOfServiceAccrual(
                        company.getId(),
                        employee.getId(),
                        yearMonth,
                        amount,
                        pair.getDebitAccountId(),
                        pair.getCreditAccountId(),
                        "EOSB accrual " + period + " — " + label,
                        postingDate);
                posted++;
            }
        }
        return posted;
    }

    private static String employeeLabel(Employee employee) {
        if (employee.getEmployeeNo() != null && !employee.getEmployeeNo().isBlank()) {
            return employee.getEmployeeNo();
        }
        String name = ((employee.getFirstName() != null ? employee.getFirstName() : "")
                + " "
                + (employee.getLastName() != null ? employee.getLastName() : "")).trim();
        return name.isEmpty() ? String.valueOf(employee.getId()) : name;
    }
}
