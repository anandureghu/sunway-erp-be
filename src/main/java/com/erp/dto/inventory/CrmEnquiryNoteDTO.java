package com.erp.dto.inventory;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CrmEnquiryNoteDTO {
    private Long id;
    private String body;
    private String createdBy;
    private Instant createdAt;
}
