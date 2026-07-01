package com.erp.dto.history;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoryPageResponse {
    private List<HistoryRecordDTO> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
}
