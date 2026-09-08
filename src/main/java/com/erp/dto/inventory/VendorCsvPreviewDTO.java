package com.erp.dto.inventory;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class VendorCsvPreviewDTO {
    private List<String> headers;
    private Map<String, String> fieldMapping;
    private boolean aiMapped;
    private int dataRowCount;

    @Builder.Default
    private List<Map<String, String>> sampleRows = new ArrayList<>();

    @Builder.Default
    private List<String> warnings = new ArrayList<>();
}
