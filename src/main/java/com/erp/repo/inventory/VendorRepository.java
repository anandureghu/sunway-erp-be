package com.erp.repo.inventory;

import com.erp.domain.inventory.Vendor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VendorRepository extends JpaRepository<Vendor, Long> {
    Vendor findByVendorId(Long vendorId);
}
