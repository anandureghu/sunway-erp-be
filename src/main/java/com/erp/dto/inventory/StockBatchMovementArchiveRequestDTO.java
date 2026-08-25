package com.erp.dto.inventory;

import lombok.Data;

import java.util.List;

@Data
public class StockBatchMovementArchiveRequestDTO {
    private List<Long> ids;
}
