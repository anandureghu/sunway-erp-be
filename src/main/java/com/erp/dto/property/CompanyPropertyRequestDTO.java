package com.erp.dto.property;

import com.erp.domain.enums.PropertyStatus;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CompanyPropertyRequestDTO {

    // Used only for UPDATE
    private Long id;

    private String itemCode;
    private String itemName;

    // ASSIGNED / RETURNED / LOST / DAMAGED
    private PropertyStatus itemStatus;

    // REQUIRED when ASSIGNED
    private LocalDate dateGiven;

    // REQUIRED when RETURNED / LOST
    private LocalDate returnDate;

    private String description;
}
