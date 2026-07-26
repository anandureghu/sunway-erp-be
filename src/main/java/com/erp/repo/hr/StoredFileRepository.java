package com.erp.repo.hr;

import com.erp.domain.hr.StoredFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface StoredFileRepository extends JpaRepository<StoredFile, Long> {

    Optional<StoredFile> findByBlobPath(String blobPath);

    @Query("select coalesce(sum(s.sizeBytes), 0) from StoredFile s where s.companyId = :companyId")
    long sumSizeBytesByCompanyId(@Param("companyId") Long companyId);
}
