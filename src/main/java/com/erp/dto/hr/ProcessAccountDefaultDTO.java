package com.erp.dto.hr;

import com.erp.domain.finance.AccountingProcessCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessAccountDefaultDTO {
    private Long id;
    private AccountingProcessCode processCode;
    private Long debitAccountId;
    private Long creditAccountId;
}
