package com.erp.dto.hr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Default GL and bank accounts for sales, purchase, and invoicing.
 * Updated only via {@code PUT /api/companies/{id}/accounting-defaults}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountingDefaultsDTO {
    private Long defaultSalesDebitAccountId;
    private Long defaultSalesCreditAccountId;
    private Long defaultPurchaseDebitAccountId;
    private Long defaultPurchaseCreditAccountId;
    private Long defaultBankAccountId;
}
