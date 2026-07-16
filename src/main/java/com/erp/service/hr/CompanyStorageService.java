package com.erp.service.hr;

import com.erp.domain.hr.CompanyStorageStats;
import com.erp.dto.hr.StorageUsageDTO;
import com.erp.repo.hr.CompanyRepository;
import com.erp.repo.hr.CompanyStorageStatsRepository;
import com.erp.repo.hr.StoredFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Cloud storage is summed live from {@code stored_files} (cheap, indexed). Database storage has
 * no exact per-tenant byte count available — the schema is shared across all companies with a
 * {@code company_id} column, not partitioned per tenant — so it's estimated as
 * (row count for that company) x (avg row length) across every company-scoped table, and the
 * result is cached in {@code company_storage_stats} since the scan is too expensive to run on
 * every page load.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyStorageService {

    private final StoredFileRepository storedFileRepository;
    private final CompanyStorageStatsRepository storageStatsRepository;
    private final CompanyRepository companyRepository;
    private final JdbcTemplate jdbcTemplate;

    public long getCloudStorageBytes(Long companyId) {
        return storedFileRepository.sumSizeBytesByCompanyId(companyId);
    }

    public StorageUsageDTO getStorageUsage(Long companyId) {
        CompanyStorageStats stats = storageStatsRepository.findById(companyId).orElse(null);
        return StorageUsageDTO.builder()
                .cloudStorageBytes(getCloudStorageBytes(companyId))
                .databaseStorageBytes(stats != null ? stats.getDatabaseStorageBytes() : 0L)
                .databaseStorageCalculatedAt(stats != null ? stats.getCalculatedAt() : null)
                .build();
    }

    @Transactional
    public StorageUsageDTO recalculateDatabaseStorage(Long companyId) {
        long estimatedBytes = estimateDatabaseStorageBytes(companyId);

        CompanyStorageStats stats = storageStatsRepository.findById(companyId)
                .orElseGet(() -> CompanyStorageStats.builder().companyId(companyId).build());
        stats.setDatabaseStorageBytes(estimatedBytes);
        stats.setCalculatedAt(Instant.now());
        storageStatsRepository.save(stats);

        return getStorageUsage(companyId);
    }

    /** Refreshes the cached database-storage estimate for every company; run nightly by CompanyStorageStatsJob. */
    public void recalculateAllCompanies() {
        companyRepository.findAll().forEach(company -> {
            try {
                recalculateDatabaseStorage(company.getId());
            } catch (Exception e) {
                log.warn("Failed to recalculate storage stats for company {}", company.getId(), e);
            }
        });
    }

    private long estimateDatabaseStorageBytes(Long companyId) {
        List<Map<String, Object>> tables = jdbcTemplate.queryForList(
                "SELECT t.table_name AS table_name, t.avg_row_length AS avg_row_length " +
                        "FROM information_schema.tables t " +
                        "JOIN information_schema.columns c " +
                        "  ON c.table_schema = t.table_schema AND c.table_name = t.table_name " +
                        "WHERE t.table_schema = DATABASE() AND c.column_name = 'company_id'"
        );

        long totalBytes = 0;
        for (Map<String, Object> row : tables) {
            String tableName = String.valueOf(row.get("table_name"));
            Number avgRowLength = (Number) row.get("avg_row_length");
            if (avgRowLength == null || avgRowLength.longValue() == 0) {
                continue;
            }

            // Table name comes from information_schema (our own schema metadata), not user
            // input, and can't be bound as a JDBC parameter — safe to inline.
            Long rowCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM `" + tableName + "` WHERE company_id = ?",
                    Long.class, companyId
            );
            if (rowCount != null) {
                totalBytes += rowCount * avgRowLength.longValue();
            }
        }
        return totalBytes;
    }
}
