package com.erp.dto.contact;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeContactInfoResponseDTO {

    private String email;
    private String phone;
    private String altPhone;
    private String notes;

    private List<EmployeeAddressResponseDTO> addresses;
}
