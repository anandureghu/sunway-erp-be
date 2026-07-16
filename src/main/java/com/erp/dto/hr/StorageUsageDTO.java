package com.erp.dto.hr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StorageUsageDTO {
    private long cloudStorageBytes;
    private long databaseStorageBytes;
    private Instant databaseStorageCalculatedAt;
}
