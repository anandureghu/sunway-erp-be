package com.erp.dto.finance;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JournalEntryResponseDTO {
    private Long id;
    private String journalEntryNumber;
    private String description;
    private LocalDate entryDate;
    private String status;
    private BigDecimal totalDebit;
    private BigDecimal totalCredit;
    private Instant postedAt;
    private Instant reversedAt;
    private Long reversalEntryId;
    private List<JournalLineDTO> lines;
}
