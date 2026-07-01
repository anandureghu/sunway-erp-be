package com.erp.dto.sales;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicDeliveryTrackingCompanyDTO {
    private String companyCode;
    private String companyName;
    private String logoUrl;
    private String phoneNo;
    private String companyEmail;
    private String websiteUrl;
}
