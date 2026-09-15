package com.erp.dto.hrsettings;

import lombok.Builder;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class JobCodeCsvImportResultDTO {
    private int created;
    private int skipped;
    private int failed;
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
