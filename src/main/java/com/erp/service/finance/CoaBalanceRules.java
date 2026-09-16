package com.erp.service.finance;

import com.erp.domain.finance.ChartOfAccounts;
import com.erp.domain.finance.COAType;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

/**
 * Ensures postings do not drive selected COA balances below zero (insufficient funds /
 * invalid natural balance for this model).
 */
public final class CoaBalanceRules {

    private CoaBalanceRules() {
    }

    /**
     * Types where stored balance may legitimately cross zero in either direction.
     * Only BUDGET accounts are hard-blocked from going negative — overspending a
     * budget allocation is a genuine business-rule violation.  All other account
     * types (ASSET, LIABILITY, EQUITY, REVENUE, EXPENSE, INCOME, CASH, TAX, COST)
     * follow normal double-entry accounting rules and may carry a negative trail
     * balance depending on the posting sequence (e.g., an asset account can show
     * a negative balance before the corresponding opening-balance entry is made).
     */
    private static boolean allowsNegativeResultingBalance(COAType type) {
        if (type == null) {
            return true;
        }
        return type != COAType.BUDGET;
    }

    /**
     * @param delta change applied to {@link ChartOfAccounts#getBalance()} (same semantics as
     *              {@code current + delta})
     */
    public static void assertSufficientBalance(ChartOfAccounts coa, BigDecimal delta) {
        if (allowsNegativeResultingBalance(coa.getType())) {
            return;
        }
        BigDecimal current = coa.getBalance() == null ? BigDecimal.ZERO : coa.getBalance();
        BigDecimal next = current.add(delta);
        if (next.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Insufficient balance on account "
                            + coa.getAccountCode()
                            + " ("
                            + coa.getAccountName()
                            + "): current "
                            + current
                            + ", would become "
                            + next
                            + " after posting");
        }
    }
}
