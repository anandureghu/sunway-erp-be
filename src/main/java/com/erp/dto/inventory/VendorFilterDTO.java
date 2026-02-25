package com.erp.dto.inventory;

import lombok.Data;

@Data
public class VendorFilterDTO {
    private String vendorName;
    private String city;
    private Boolean approved;
    private Boolean rejected;
    private Boolean isActive;
}