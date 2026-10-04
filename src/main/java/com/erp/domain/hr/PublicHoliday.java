package com.erp.domain.hr;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A company public holiday (HR Settings → Policies → Public Holidays). May span
 * several days (e.g. Eid al-Fitr). Working days (Sun–Thu) inside it are paid days
 * off: no absence, no leave deduction, paid by payroll; work done on them is
 * holiday overtime.
 */
@Entity
@Table(name = "company_public_holidays")
@Getter
@Setter
public class PublicHoliday {

    public enum Category { NATIONAL, RELIGIOUS, COMPANY }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private Category category = Category.NATIONAL;

    /** Same calendar date every year (e.g. National Day, 18 Dec) — copied as-is to the next year. */
    @Column(name = "recurring", nullable = false)
    private boolean recurring;

    /** False while the dates are an estimate (e.g. Eid before the moon-sighting announcement). */
    @Column(name = "confirmed", nullable = false)
    private boolean confirmed = true;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    private void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    private void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
