package com.erp.dto.finance;

import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JournalEntryCreateDTO {
    private String description;
    private LocalDate entryDate;
    private String source;
    private Long periodId;
    private List<JournalLineDTO> lines;
}
