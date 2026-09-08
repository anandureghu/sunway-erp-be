package com.erp.dto.inventory;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class CategoryCsvPreviewDTO {

    private List<String> headers;

    /** Source header → canonical field (null = ignored, e.g. Arabic columns). */
    private Map<String, String> fieldMapping;

    private boolean aiMapped;

    private int dataRowCount;

    /** First few rows with values keyed by canonical field. */
    @Builder.Default
    private List<Map<String, String>> sampleRows = new ArrayList<>();

    @Builder.Default
    private List<String> warnings = new ArrayList<>();
}
