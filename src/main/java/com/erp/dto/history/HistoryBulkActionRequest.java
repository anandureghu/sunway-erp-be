package com.erp.dto.history;

import com.erp.domain.history.HistoryEntityType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HistoryBulkActionRequest {
    private HistoryEntityType type;
    private List<Long> ids;
}
