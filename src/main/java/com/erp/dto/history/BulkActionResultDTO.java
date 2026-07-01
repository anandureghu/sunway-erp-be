package com.erp.dto.history;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkActionResultDTO {
    @Builder.Default
    private List<Long> succeeded = new ArrayList<>();
    @Builder.Default
    private List<BulkActionFailureDTO> failed = new ArrayList<>();
}
