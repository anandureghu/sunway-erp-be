package com.erp.dto.purchase;

import lombok.Data;

import java.util.List;

@Data
public class PurchaseRequisitionCreateDTO {
    private List<PurchaseRequisitionItemDTO> items;
}
