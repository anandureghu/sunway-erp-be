package com.erp.dto.admin;

import com.erp.domain.admin.EnquiryStatus;
import lombok.Data;

@Data
public class EnquiryUpdateRequest {
    private EnquiryStatus status;
    private String notes;
}
