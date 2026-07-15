package com.erp.dto.dashboard.hr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HrDocumentsExpiringDTO {

    private long qidExpiring;
    private long passportExpiring;

    /** Aliases qidExpiring — this schema models QID and visa as one ResidencePermit record. */
    private long visaExpiring;

    private long contractsExpiring;

    /** Stubbed to 0 — no other-document-type entity exists yet. */
    private long otherDocsExpiring;
}
