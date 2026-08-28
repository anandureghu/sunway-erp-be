package com.erp.dto.inventory;

import lombok.Data;

import java.util.List;

@Data
public class ItemBulkIdsRequestDTO {

    private List<Long> itemIds;
}
