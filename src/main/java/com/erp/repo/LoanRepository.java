package com.erp.repo;

import com.erp.domain.Loan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

public interface LoanRepository extends JpaRepository<Loan, Long> {

    List<Loan> findByEmployeeIdOrderByStartDateDesc(Long employeeId);

    Optional<Loan> findTopByEmployeeIdOrderByStartDateDesc(Long employeeId);

    // ✅ No named params → no need for -parameters or @Param
    @Query(
            "select l " +
                    "from Loan l " +
                    "where l.employee.id = ?1 " +
                    "  and lower(l.loanStatus) = 'active' " +
                    "  and l.startDate <= ?2 " +
                    "  and (l.balance is null or l.balance > 0)"
    )
    List<Loan> findActiveLoansForMonth(Long employeeId, LocalDate monthEnd);

    // Optional: handy overload
    default List<Loan> findActiveLoansForMonth(Long employeeId, YearMonth ym) {
        return findActiveLoansForMonth(employeeId, ym.atEndOfMonth());
    }

    // Optional dropdown helpers (also positional)
    @Query("select distinct l.loanType from Loan l order by l.loanType asc")
    List<String> findDistinctLoanTypes();

    @Query("select distinct l.loanStatus from Loan l order by l.loanStatus asc")
    List<String> findDistinctLoanStatuses();
}
