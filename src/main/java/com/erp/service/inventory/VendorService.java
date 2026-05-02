package com.erp.service.inventory;

import com.erp.domain.hr.Company;
import com.erp.domain.inventory.Vendor;
import com.erp.dto.inventory.VendorCreateDTO;
import com.erp.dto.inventory.VendorFilterDTO;
import com.erp.dto.inventory.VendorResponseDTO;
import com.erp.dto.inventory.VendorUpdateDTO;
import com.erp.repo.inventory.VendorRepository;
import com.erp.security.context.AuthContext;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class VendorService {

    private final VendorRepository vendorRepo;
    private final AuthContext authContext;

    public VendorService(VendorRepository vendorRepo, AuthContext authContext) {
        this.vendorRepo = vendorRepo;
        this.authContext = authContext;
    }

    // ---------------- LIST ----------------
    public List<VendorResponseDTO> getAllVendors() {
        Long companyId = authContext.getCurrentCompanyId();
        return vendorRepo.findByCompanyIdOrderByCreatedAtDesc(companyId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    public Boolean approveVendor(Long id, boolean status) {
        Vendor vendor = vendorRepo.findById(id).orElseThrow(() -> new RuntimeException("vendor not exist"));
        if (status) {
            vendor.setApproved(true);
        } else {
            vendor.setRejected(true);
        }
        vendorRepo.save(vendor);
        return status;
    }

    public Page<VendorResponseDTO> getFilteredVendors(
            VendorFilterDTO filter,
            Pageable pageable
    ) {

        Specification<Vendor> spec = buildSpecification(filter);

        return vendorRepo.findAll(spec, pageable)
                .map(this::toDTO);
    }

    // ---------------- GET BY ID ----------------
    public VendorResponseDTO getVendorDTO(Long id) {
        Vendor v = getVendorById(id);
        validateCompany(v.getCompany().getId());
        return toDTO(v);
    }

    private Vendor getVendorById(Long id) {
        return vendorRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Vendor not found"));
    }

    private void validateCompany(Long vendorCompanyId) {
        if (!vendorCompanyId.equals(authContext.getCurrentCompanyId())) {
            throw new RuntimeException("Unauthorized access");
        }
    }

    // ---------------- CREATE ----------------
    public VendorResponseDTO createVendor(VendorCreateDTO dto) {
        Long companyId = authContext.getCurrentCompanyId();

        Vendor vendor = Vendor.builder()
                .vendorName(dto.getVendorName())
                .taxId(dto.getTaxId())
                .paymentTerms(dto.getPaymentTerms())
                .currencyCode(dto.getCurrencyCode())
                .creditLimit(dto.getCreditLimit())
                .is1099Vendor(dto.is1099Vendor())
                .isActive(dto.isActive())
                .street(dto.getStreet())
                .city(dto.getCity())
                .country(dto.getCountry())
                .phoneNo(dto.getPhoneNo())
                .email(dto.getEmail())
                .contactPersonName(dto.getContactPersonName())
                .fax(dto.getFax())
                .remarks(dto.getRemarks())
                .websiteUrl(dto.getWebsiteUrl())
                .approved(false)
                .company(Company.builder().id(companyId).build())
                .build();

        return toDTO(vendorRepo.save(vendor));
    }

    // ---------------- UPDATE ----------------
    public VendorResponseDTO updateVendor(Long id, VendorUpdateDTO dto) {
        Vendor v = getVendorById(id);
        validateCompany(v.getCompany().getId());

        if (dto.getVendorName() != null) v.setVendorName(dto.getVendorName());
        if (dto.getTaxId() != null) v.setTaxId(dto.getTaxId());
        if (dto.getPaymentTerms() != null) v.setPaymentTerms(dto.getPaymentTerms());
        if (dto.getCurrencyCode() != null) v.setCurrencyCode(dto.getCurrencyCode());
        if (dto.getCreditLimit() != null) v.setCreditLimit(dto.getCreditLimit());

        if (dto.getIs1099Vendor() != null) v.set1099Vendor(dto.getIs1099Vendor());
        if (dto.getIsActive() != null) v.setActive(dto.getIsActive());

        if (dto.getStreet() != null) v.setStreet(dto.getStreet());
        if (dto.getCity() != null) v.setCity(dto.getCity());
        if (dto.getCountry() != null) v.setCountry(dto.getCountry());
        if (dto.getPhoneNo() != null) v.setPhoneNo(dto.getPhoneNo());
        if (dto.getEmail() != null) v.setEmail(dto.getEmail());
        if (dto.getEmail() != null) v.setEmail(dto.getEmail());
        if (dto.getRemarks() != null) v.setRemarks(dto.getRemarks());

        if (dto.getContactPersonName() != null) v.setContactPersonName(dto.getContactPersonName());
        if (dto.getFax() != null) v.setFax(dto.getFax());
        if (dto.getWebsiteUrl() != null) v.setWebsiteUrl(dto.getWebsiteUrl());

        return toDTO(vendorRepo.save(v));
    }

    // ---------------- DELETE ----------------
    public void deleteVendor(Long id) {
        Vendor v = getVendorById(id);
        validateCompany(v.getCompany().getId());
        vendorRepo.delete(v);
    }

    // ---------------- MAPPER ----------------
    private VendorResponseDTO toDTO(Vendor v) {
        return VendorResponseDTO.builder()
                .id(v.getId())
                .vendorName(v.getVendorName())
                .taxId(v.getTaxId())
                .paymentTerms(v.getPaymentTerms())
                .currencyCode(v.getCurrencyCode())
                .creditLimit(v.getCreditLimit())
                .is1099Vendor(v.is1099Vendor())
                .isActive(v.isActive())
                .street(v.getStreet())
                .city(v.getCity())
                .country(v.getCountry())
                .phoneNo(v.getPhoneNo())
                .email(v.getEmail())
                .contactPersonName(v.getContactPersonName())
                .fax(v.getFax())
                .approved(v.isApproved())
                .rejected(v.isRejected())
                .remarks(v.getRemarks())
                .websiteUrl(v.getWebsiteUrl())
                .companyId(v.getCompany().getId())
                .build();
    }

    public Specification<Vendor> buildSpecification(VendorFilterDTO filter) {
        return (root, query, cb) -> {

            List<Predicate> predicates = new ArrayList<>();

            if (filter.getVendorName() != null) {
                predicates.add(
                        cb.like(
                                cb.lower(root.get("vendorName")),
                                "%" + filter.getVendorName().toLowerCase() + "%"
                        )
                );
            }

            if (filter.getCity() != null) {
                predicates.add(
                        cb.equal(
                                cb.lower(root.get("city")),
                                filter.getCity().toLowerCase()
                        )
                );
            }

            if (filter.getApproved() != null) {
                predicates.add(
                        cb.equal(root.get("approved"), filter.getApproved())
                );
            }

            if (filter.getRejected() != null) {
                predicates.add(
                        cb.equal(root.get("rejected"), filter.getRejected())
                );
            }

            if (filter.getIsActive() != null) {
                predicates.add(
                        cb.equal(root.get("isActive"), filter.getIsActive())
                );
            }

            // Always filter by company
            predicates.add(
                    cb.equal(root.get("company").get("id"),
                            authContext.getCurrentCompanyId())
            );

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
