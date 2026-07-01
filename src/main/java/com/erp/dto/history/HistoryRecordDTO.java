package com.erp.dto.history;

import com.erp.domain.history.HistoryEntityType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoryRecordDTO {
    private Long id;
    private HistoryEntityType type;
    private String referenceNo;
    private String status;
    private String partyName;
    private BigDecimal amount;
    private Instant createdAt;
    private Instant archivedAt;
}
