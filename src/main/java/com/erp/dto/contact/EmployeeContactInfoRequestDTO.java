package com.erp.dto.contact;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeContactInfoRequestDTO {

    private String email;
    private String phone;
    private String altPhone;
}
