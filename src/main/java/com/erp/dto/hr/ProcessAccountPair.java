package com.erp.dto.hr;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProcessAccountPair {
    private Long debitAccountId;
    private Long creditAccountId;

    public boolean isComplete() {
        return debitAccountId != null && creditAccountId != null;
    }
}
