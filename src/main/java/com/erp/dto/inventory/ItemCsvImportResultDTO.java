package com.erp.dto.inventory;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class ItemCsvImportResultDTO {

    private int created;
    private int skipped;
    private int failed;

    /** How source CSV headers were mapped to canonical item fields. */
    private Map<String, String> fieldMapping;

    /** True when OpenAI produced the mapping; false for heuristic fallback. */
    private boolean aiMapped;

    @Builder.Default
    private List<RowError> errors = new ArrayList<>();

    @Data
    @Builder
    public static class RowError {
        private int row;
        private String sku;
        private String message;
    }
}
