package com.erp.dto.dashboard.hr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HrActivityItemDTO {

    private String description;
    private String employeeName;
    private Instant occurredAt;
}
