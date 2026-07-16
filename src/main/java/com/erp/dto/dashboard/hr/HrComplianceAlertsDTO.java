package com.erp.dto.dashboard.hr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HrComplianceAlertsDTO {

    private long qidExpiring30d;
    private long passportExpiring30d;

    /** Stubbed to 0 — no vaccination-record entity exists yet. */
    private long vaccinationExpiring30d;

    private long contractsExpiring30d;

    /** Stubbed to 0 — no probation-end field exists yet. */
    private long probationEndingSoon;
}
