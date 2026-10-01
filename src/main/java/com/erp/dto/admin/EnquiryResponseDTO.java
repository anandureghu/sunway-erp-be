package com.erp.dto.admin;

import com.erp.domain.admin.EnquiryChannel;
import com.erp.domain.admin.EnquiryStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class EnquiryResponseDTO {
    private Long id;
    private String name;
    private String email;
    private String phone;
    private String interest;
    private String message;
    private EnquiryChannel channel;
    private EnquiryStatus status;
    private String notes;
    private Instant createdAt;
    private Instant updatedAt;
}
