package com.erp.dto.property;

import com.erp.domain.PropertyStatus;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CompanyPropertyRequestDTO {

    private String itemCode;
    private String itemName;
    private PropertyStatus itemStatus;
    private LocalDate dateGiven;
    private LocalDate returnDate;
    private String description;
}
