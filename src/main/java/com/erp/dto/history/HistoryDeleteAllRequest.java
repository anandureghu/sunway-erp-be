package com.erp.dto.history;

import com.erp.domain.history.HistoryEntityType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HistoryDeleteAllRequest {
    private HistoryEntityType type;
    private String confirmToken;
}
