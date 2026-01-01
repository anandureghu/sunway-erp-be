package com.erp.dto.property;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter @Setter
public class CompanyPropertyResponseDTO {

    private Long id;
    private String itemCode;
    private String itemName;
    private String itemStatus;
    private String description;
    private LocalDate dateGiven;
    private LocalDate returnDate;
}
