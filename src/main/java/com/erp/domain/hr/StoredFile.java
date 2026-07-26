package com.erp.domain.hr;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** Ledger of uploaded blob sizes, keyed by blob path, used to sum a company's cloud storage usage. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "stored_files")
public class StoredFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "blob_path", length = 500, nullable = false, unique = true)
    private String blobPath;

    @Column(name = "container", length = 20, nullable = false)
    private String container;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
