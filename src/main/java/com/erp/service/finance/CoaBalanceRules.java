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
     * Types where stored balance may legitimately cross zero in either direction in this app.
     */
    private static boolean allowsNegativeResultingBalance(COAType type) {
        if (type == null) {
            return false;
        }
        return switch (type) {
            case LIABILITY, EQUITY, REVENUE, INCOME -> true;
            default -> false;
        };
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
