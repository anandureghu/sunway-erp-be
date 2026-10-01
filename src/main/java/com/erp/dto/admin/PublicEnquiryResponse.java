package com.erp.dto.admin;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PublicEnquiryResponse {
    private Long id;
    /** True when a new row was inserted; false when an existing email was updated. */
    private boolean created;
}
