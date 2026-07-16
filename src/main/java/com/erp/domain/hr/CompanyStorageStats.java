package com.erp.domain.hr;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** Cached snapshot of a company's estimated database storage, refreshed by CompanyStorageStatsJob. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "company_storage_stats")
public class CompanyStorageStats {

    @Id
    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "database_storage_bytes", nullable = false)
    private long databaseStorageBytes;

    @Column(name = "calculated_at")
    private Instant calculatedAt;
}
