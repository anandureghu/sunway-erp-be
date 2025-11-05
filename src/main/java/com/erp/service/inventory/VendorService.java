package com.erp.service.inventory;

import com.erp.domain.inventory.Vendor;
import com.erp.repo.inventory.VendorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;

@Service
@Transactional
public class VendorService {

    private final VendorRepository vendorRepo;

    public VendorService(VendorRepository vendorRepo) {
        this.vendorRepo = vendorRepo;
    }

    public List<Vendor> getAllVendors() {
        return vendorRepo.findAll();
    }

    public Vendor getVendorById(Long id) {
        return vendorRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Vendor not found"));
    }

    public Vendor createVendor(Vendor vendor) {
        vendor.setCreatedAt(Instant.now());
        return vendorRepo.save(vendor);
    }

    public Vendor updateVendor(Long id, Vendor updated) {
        Vendor existing = getVendorById(id);
        existing.setVendorName(updated.getVendorName());
        existing.setStreet(updated.getStreet());
        existing.setCity(updated.getCity());
        existing.setCountry(updated.getCountry());
        existing.setPhoneNo(updated.getPhoneNo());
        return vendorRepo.save(existing);
    }

    public void deleteVendor(Long id) {
        vendorRepo.deleteById(id);
    }
}
