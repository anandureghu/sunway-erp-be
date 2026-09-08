package com.erp.dto.inventory;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class CategoryCsvImportResultDTO {

    private int created;
    private int skipped;
    private int failed;
    private int parentsCreated;
    private int subCategoriesCreated;

    private Map<String, String> fieldMapping;
    private boolean aiMapped;

    @Builder.Default
    private List<RowError> errors = new ArrayList<>();

    @Data
    @Builder
    public static class RowError {
        private int row;
        private String code;
        private String message;
    }
}
