package com.erp.dto.hr;

import lombok.Data;

import java.time.LocalDate;

/**
 * Optional body for renewing a contract. When {@code expirationDate} is null the
 * service extends the contract by its own period (or 12 months) from its current
 * expiry (or today, if it already lapsed).
 */
@Data
public class ContractRenewRequestDTO {
    private LocalDate expirationDate;
}
