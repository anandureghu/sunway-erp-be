package com.erp.dto.hr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayrollExportSettingsDTO {

    private String payrollEmployerEid;
    private String payrollPayerEid;
    private String payrollPayerQid;
    private String payrollPayerBankShortName;
    private String payrollPayerIban;
    /** Defaults to "1" when not set */
    private String payrollSifVersion;
}
