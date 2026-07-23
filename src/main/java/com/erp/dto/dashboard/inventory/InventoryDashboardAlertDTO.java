package com.erp.dto.dashboard.inventory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryDashboardAlertDTO {

    private String type;
    private String message;
    private long count;
    private BigDecimal amount;
}
