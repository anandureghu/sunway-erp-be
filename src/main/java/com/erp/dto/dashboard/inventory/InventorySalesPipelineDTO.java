package com.erp.dto.dashboard.inventory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventorySalesPipelineDTO {

    private long quotations;
    private long confirmed;
    private long shipmentsInTransit;
    private long deliveredThisMonth;
}
