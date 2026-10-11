package com.erp.dto.hrsettings;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** Position Approvals: holds waiting for a decision, and the latest decisions. */
@Getter
@Builder
public class JobCodeHoldQueueDTO {
    /** Job codes ON_HOLD pending approval, oldest hold first. */
    private List<JobCodeResponseDTO> pending;
    /** Most recently approved / rejected holds, newest first. */
    private List<JobCodeResponseDTO> recent;
}
