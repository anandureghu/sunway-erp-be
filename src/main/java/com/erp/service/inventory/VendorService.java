package com.erp.service.inventory;

import com.erp.domain.hr.Company;
import com.erp.domain.inventory.Vendor;
import com.erp.domain.purchase.PurchaseOrder;
import com.erp.domain.purchase.PurchaseOrderStatus;
import com.erp.dto.inventory.VendorCreateDTO;
import com.erp.dto.inventory.VendorFilterDTO;
import com.erp.dto.inventory.VendorResponseDTO;
import com.erp.dto.inventory.VendorUpdateDTO;
import com.erp.exception.ConflictException;
import com.erp.exception.NotFoundException;
import com.erp.repo.finance.PaymentRepository;
import com.erp.repo.inventory.VendorRepository;
import com.erp.repo.purchase.PurchaseOrderRepository;
import com.erp.security.context.AuthContext;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class VendorService {

    private static final List<PurchaseOrderStatus> OPEN_PO_STATUSES = List.of(
            PurchaseOrderStatus.DRAFT,
            PurchaseOrderStatus.APPROVED,
            PurchaseOrderStatus.CONFIRMED,
            PurchaseOrderStatus.PARTIALLY_RECEIVED
    );

    private final VendorRepository vendorRepo;
    private final PurchaseOrderRepository purchaseOrderRepo;
    private final PaymentRepository paymentRepo;
    private final AuthContext authContext;

    public VendorService(
            VendorRepository vendorRepo,
            PurchaseOrderRepository purchaseOrderRepo,
            PaymentRepository paymentRepo,
            AuthContext authContext
    ) {
        this.vendorRepo = vendorRepo;
        this.purchaseOrderRepo = purchaseOrderRepo;
        this.paymentRepo = paymentRepo;
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
        assertSameTenant(vendor);
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

    private void assertSameTenant(Vendor vendor) {
        if ("SUPER_ADMIN".equalsIgnoreCase(authContext.getCurrentUserRole())) return;
        Long currentCompanyId = authContext.getCurrentCompanyId();
        Long vendorCompanyId = vendor != null && vendor.getCompany() != null
                ? vendor.getCompany().getId() : null;
        if (currentCompanyId == null || vendorCompanyId == null
                || !currentCompanyId.equals(vendorCompanyId)) {
            throw new NotFoundException("Vendor not found");
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
        if (dto.getRemarks() != null) v.setRemarks(dto.getRemarks());

        if (dto.getContactPersonName() != null) v.setContactPersonName(dto.getContactPersonName());
        if (dto.getFax() != null) v.setFax(dto.getFax());
        if (dto.getWebsiteUrl() != null) v.setWebsiteUrl(dto.getWebsiteUrl());

        return toDTO(vendorRepo.save(v));
    }

    // ---------------- DELETE (blocked when the vendor has open orders/payments) ----------------
    public void deleteVendor(Long id) {
        Vendor v = getVendorById(id);
        validateCompany(v.getCompany().getId());
        assertVendorDeletable(v);
        vendorRepo.delete(v);
    }

    private void assertVendorDeletable(Vendor vendor) {
        List<PurchaseOrder> openOrders = purchaseOrderRepo
                .findBySupplier_IdAndArchivedFalseAndStatusInOrderByCreatedAtDesc(
                        vendor.getId(),
                        OPEN_PO_STATUSES
                );
        long pendingPayments = paymentRepo.countPendingVendorPaymentsForSupplier(vendor.getId());

        if (openOrders.isEmpty() && pendingPayments == 0) {
            return;
        }

        List<String> reasons = new ArrayList<>();
        if (!openOrders.isEmpty()) {
            String orderRefs = openOrders.stream()
                    .map(PurchaseOrder::getOrderNumber)
                    .limit(5)
                    .collect(Collectors.joining(", "));
            if (openOrders.size() > 5) {
                orderRefs = orderRefs + ", …";
            }
            reasons.add(
                    openOrders.size() + " open purchase order(s)"
                            + (orderRefs.isBlank() ? "" : " (" + orderRefs + ")")
            );
        }
        if (pendingPayments > 0) {
            List<String> paymentCodes = paymentRepo.findPendingVendorPaymentCodesForSupplier(vendor.getId());
            String paymentRefs = paymentCodes.stream()
                    .limit(5)
                    .filter(code -> code != null && !code.isBlank())
                    .collect(Collectors.joining(", "));
            if (pendingPayments > 5 && !paymentRefs.isBlank()) {
                paymentRefs = paymentRefs + ", …";
            }
            reasons.add(
                    pendingPayments + " pending vendor payment(s)"
                            + (paymentRefs.isBlank() ? "" : " (" + paymentRefs + ")")
            );
        }

        throw new ConflictException(
                "Cannot delete supplier \""
                        + vendor.getVendorName()
                        + "\". "
                        + String.join(" and ", reasons)
                        + ". Complete or cancel these orders and payments before deleting."
        );
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
