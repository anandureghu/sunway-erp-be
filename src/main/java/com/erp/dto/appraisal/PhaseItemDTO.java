package com.erp.dto.appraisal;

import lombok.Data;

@Data
public class PhaseItemDTO {
    private String id;
    private String label;
    private String icon;
    private String startDate;
    private String endDate;
    private Boolean enabled;
    private String description;
}