package com.erp.dto.inventory;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CrmEnquiryConvertDTO {
    /** CUSTOMER or SUPPLIER */
    private String target;
}
