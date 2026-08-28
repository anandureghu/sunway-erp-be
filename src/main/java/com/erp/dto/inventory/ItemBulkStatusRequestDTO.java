package com.erp.dto.inventory;

import lombok.Data;

import java.util.List;

@Data
public class ItemBulkStatusRequestDTO {

    private List<Long> itemIds;

    /** Catalog status: active, discontinued, out_of_stock */
    private String status;
}
