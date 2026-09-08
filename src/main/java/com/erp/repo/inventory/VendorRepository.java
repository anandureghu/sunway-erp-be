package com.erp.repo.inventory;

import com.erp.domain.inventory.Vendor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VendorRepository extends JpaRepository<Vendor, Long>, JpaSpecificationExecutor<Vendor> {
    List<Vendor> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    boolean existsByVendorCodeAndCompanyId(String vendorCode, Long companyId);

    Optional<Vendor> findByCompanyIdAndVendorCodeIgnoreCase(Long companyId, String vendorCode);
}