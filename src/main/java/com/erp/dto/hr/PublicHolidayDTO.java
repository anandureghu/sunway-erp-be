package com.erp.dto.hr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** A public holiday as shown / edited in HR Settings → Policies → Public Holidays. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicHolidayDTO {
    private Long id;
    private String name;
    private LocalDate startDate;
    private LocalDate endDate;
    /** NATIONAL, RELIGIOUS or COMPANY. */
    private String category;
    private boolean recurring;
    private boolean confirmed;
    private String notes;
    // ── read-only, computed ──
    /** Calendar days the holiday spans. */
    private Integer totalDays;
    /** Of those, working days (Sun–Thu) — the paid days off that affect attendance and payroll. */
    private Integer workingDays;
}
