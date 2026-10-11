package com.erp.dto.hrsettings;

import lombok.Getter;
import lombok.Setter;

/** Note recorded when a job-code hold is approved, rejected or released. */
@Getter
@Setter
public class JobCodeHoldDecisionDTO {
    private String note;
}
