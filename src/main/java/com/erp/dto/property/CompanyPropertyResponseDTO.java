package com.erp.dto.property;

import com.erp.domain.enums.PropertyStatus;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CompanyPropertyResponseDTO {

    private Long id;

    private String itemCode;
    private String itemName;

    private PropertyStatus itemStatus;

    private LocalDate dateGiven;
    private LocalDate returnDate;

    private String description;
}
