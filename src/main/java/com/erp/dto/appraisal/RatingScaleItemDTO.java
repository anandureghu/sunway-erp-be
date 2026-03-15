package com.erp.dto.appraisal;

import lombok.Data;

@Data
public class RatingScaleItemDTO {
    private Integer score;
    private String label;
    private String definition;
}