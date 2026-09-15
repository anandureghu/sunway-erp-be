package com.erp.dto.hr;

import lombok.Builder;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class EmployeeCsvImportResultDTO {
    private int created;
    private int updated;
    private int skipped;
    private int failed;
    @Builder.Default
    private List<RowError> errors = new ArrayList<>();

    @Data
    @Builder
    public static class RowError {
        private int row;
        private String employeeNo;
        private String message;
    }
}
