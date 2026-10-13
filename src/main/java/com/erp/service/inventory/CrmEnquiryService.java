package com.erp.service.inventory;

import com.erp.domain.User;
import com.erp.domain.hr.Company;
import com.erp.domain.inventory.*;
import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.dto.inventory.*;
import com.erp.exception.NotFoundException;
import com.erp.repo.inventory.CrmEnquiryRepository;
import com.erp.security.context.AuthContext;
import com.erp.service.security.CustomUserPrincipal;
import com.erp.service.security.PermissionCheckService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class CrmEnquiryService {

    private final CrmEnquiryRepository enquiryRepository;
    private final AuthContext authContext;
    private final PermissionCheckService permissionChecker;
    private final CustomerService customerService;
    private final VendorService vendorService;

    public Page<CrmEnquiryResponseDTO> list(
            CrmPipelineStatus pipeline,
            CrmRelationshipStage stage,
            CrmProspectType type,
            String search,
            Pageable pageable
    ) {
        assertCanView();
        Long companyId = requireCompanyId();
        String q = search == null || search.isBlank() ? null : search.trim();
        return enquiryRepository
                .search(companyId, pipeline, stage, type, q, pageable)
                .map(e -> toDto(e, false));
    }

    public CrmEnquiryResponseDTO get(Long id) {
        assertCanView();
        return toDto(findOwned(id), true);
    }

    public CrmEnquiryResponseDTO create(CrmEnquiryCreateDTO dto) {
        assertCanEdit();
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new IllegalArgumentException("Name is required");
        }
        Long companyId = requireCompanyId();
        CrmEnquiry e = CrmEnquiry.builder()
                .company(Company.builder().id(companyId).build())
                .name(dto.getName().trim())
                .organizationName(blankToNull(dto.getOrganizationName()))
                .prospectType(dto.getProspectType() != null ? dto.getProspectType() : CrmProspectType.CUSTOMER)
                .email(blankToNull(dto.getEmail()))
                .phone(blankToNull(dto.getPhone()))
                .secondaryContact(blankToNull(dto.getSecondaryContact()))
                .address(blankToNull(dto.getAddress()))
                .source(blankToNull(dto.getSource()))
                .interest(blankToNull(dto.getInterest()))
                .summary(blankToNull(dto.getSummary()))
                .pipelineStatus(dto.getPipelineStatus() != null ? dto.getPipelineStatus() : CrmPipelineStatus.NEW)
                .relationshipStage(dto.getRelationshipStage() != null
                        ? dto.getRelationshipStage()
                        : CrmRelationshipStage.PROSPECTIVE)
                .build();
        return toDto(enquiryRepository.save(e), true);
    }

    public CrmEnquiryResponseDTO update(Long id, CrmEnquiryUpdateDTO dto) {
        assertCanEdit();
        CrmEnquiry e = findOwned(id);
        if (dto.getName() != null) {
            if (dto.getName().isBlank()) throw new IllegalArgumentException("Name is required");
            e.setName(dto.getName().trim());
        }
        if (dto.getOrganizationName() != null) e.setOrganizationName(blankToNull(dto.getOrganizationName()));
        if (dto.getProspectType() != null) e.setProspectType(dto.getProspectType());
        if (dto.getEmail() != null) e.setEmail(blankToNull(dto.getEmail()));
        if (dto.getPhone() != null) e.setPhone(blankToNull(dto.getPhone()));
        if (dto.getSecondaryContact() != null) e.setSecondaryContact(blankToNull(dto.getSecondaryContact()));
        if (dto.getAddress() != null) e.setAddress(blankToNull(dto.getAddress()));
        if (dto.getSource() != null) e.setSource(blankToNull(dto.getSource()));
        if (dto.getInterest() != null) e.setInterest(blankToNull(dto.getInterest()));
        if (dto.getSummary() != null) e.setSummary(blankToNull(dto.getSummary()));
        if (dto.getPipelineStatus() != null) e.setPipelineStatus(dto.getPipelineStatus());
        if (dto.getRelationshipStage() != null) e.setRelationshipStage(dto.getRelationshipStage());
        return toDto(enquiryRepository.save(e), true);
    }

    public CrmEnquiryResponseDTO addNote(Long id, CrmEnquiryNoteCreateDTO dto) {
        assertCanEdit();
        if (dto.getBody() == null || dto.getBody().isBlank()) {
            throw new IllegalArgumentException("Note body is required");
        }
        CrmEnquiry e = findOwned(id);
        String author = resolveAuthor();
        CrmEnquiryNote note = CrmEnquiryNote.builder()
                .enquiry(e)
                .body(dto.getBody().trim())
                .createdBy(author)
                .build();
        e.getNotes().add(note);
        return toDto(enquiryRepository.save(e), true);
    }

    public CrmEnquiryResponseDTO convert(Long id, CrmEnquiryConvertDTO dto) {
        assertCanEdit();
        String target = dto.getTarget() == null ? "" : dto.getTarget().trim().toUpperCase();
        CrmEnquiry e = findOwned(id);

        if ("CUSTOMER".equals(target)) {
            if (e.getCustomerId() != null) {
                throw new IllegalArgumentException("Already converted to a customer");
            }
            CustomerResponseDTO customer = customerService.createCustomer(CustomerCreateDTO.builder()
                    .customerName(e.getOrganizationName() != null && !e.getOrganizationName().isBlank()
                            ? e.getOrganizationName()
                            : e.getName())
                    .paymentTerms("NET_30")
                    .currencyCode("QAR")
                    .creditLimit(BigDecimal.ZERO)
                    .isActive(true)
                    .email(e.getEmail())
                    .phoneNo(e.getPhone())
                    .contactPersonName(e.getName())
                    .street(e.getAddress())
                    .customerType("FROM_ENQUIRY")
                    .build());
            e.setCustomerId(customer.getId());
            e.setPipelineStatus(CrmPipelineStatus.WON);
            if (e.getRelationshipStage() == CrmRelationshipStage.PROSPECTIVE) {
                e.setRelationshipStage(CrmRelationshipStage.CURRENT);
            }
        } else if ("SUPPLIER".equals(target)) {
            if (e.getVendorId() != null) {
                throw new IllegalArgumentException("Already converted to a supplier");
            }
            VendorResponseDTO vendor = vendorService.createVendor(VendorCreateDTO.builder()
                    .vendorName(e.getOrganizationName() != null && !e.getOrganizationName().isBlank()
                            ? e.getOrganizationName()
                            : e.getName())
                    .paymentTerms("NET_30")
                    .currencyCode("QAR")
                    .creditLimit(BigDecimal.ZERO)
                    .isActive(true)
                    .email(e.getEmail())
                    .phoneNo(e.getPhone())
                    .contactPersonName(e.getName())
                    .street(e.getAddress())
                    .remarks("Converted from CRM enquiry #" + e.getId())
                    .build());
            e.setVendorId(vendor.getId());
            e.setPipelineStatus(CrmPipelineStatus.WON);
            if (e.getRelationshipStage() == CrmRelationshipStage.PROSPECTIVE) {
                e.setRelationshipStage(CrmRelationshipStage.CURRENT);
            }
        } else {
            throw new IllegalArgumentException("target must be CUSTOMER or SUPPLIER");
        }

        return toDto(enquiryRepository.save(e), true);
    }

    public void delete(Long id) {
        assertCanEdit();
        enquiryRepository.delete(findOwned(id));
    }

    private CrmEnquiry findOwned(Long id) {
        Long companyId = requireCompanyId();
        return enquiryRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new NotFoundException("CRM enquiry not found: " + id));
    }

    private Long requireCompanyId() {
        Long companyId = authContext.getCurrentCompanyId();
        if (companyId == null) throw new IllegalStateException("Company context required");
        return companyId;
    }

    private void assertCanView() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean ok = permissionChecker.hasAny(auth, AppModule.INVENTORY_SALES, AppAction.VIEW_ALL, AppAction.VIEW_OWN)
                || permissionChecker.hasAny(auth, AppModule.INVENTORY_PURCHASE, AppAction.VIEW_ALL, AppAction.VIEW_OWN);
        if (!ok) throw new SecurityException("CRM access denied");
    }

    private void assertCanEdit() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean ok = permissionChecker.has(auth, AppModule.INVENTORY_SALES, AppAction.CREATE)
                || permissionChecker.has(auth, AppModule.INVENTORY_SALES, AppAction.EDIT)
                || permissionChecker.has(auth, AppModule.INVENTORY_PURCHASE, AppAction.CREATE)
                || permissionChecker.has(auth, AppModule.INVENTORY_PURCHASE, AppAction.EDIT);
        if (!ok) throw new SecurityException("CRM edit access denied");
    }

    private String resolveAuthor() {
        User user = authContext.getCurrentUser();
        if (user != null && user.getUsername() != null) return user.getUsername();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserPrincipal p) {
            return p.getUsername();
        }
        return "user";
    }

    private CrmEnquiryResponseDTO toDto(CrmEnquiry e, boolean includeNotes) {
        List<CrmEnquiryNoteDTO> notes = List.of();
        if (includeNotes && e.getNotes() != null) {
            notes = e.getNotes().stream()
                    .sorted(Comparator.comparing(CrmEnquiryNote::getCreatedAt,
                            Comparator.nullsLast(Comparator.reverseOrder())))
                    .map(n -> CrmEnquiryNoteDTO.builder()
                            .id(n.getId())
                            .body(n.getBody())
                            .createdBy(n.getCreatedBy())
                            .createdAt(n.getCreatedAt())
                            .build())
                    .toList();
        }

        return CrmEnquiryResponseDTO.builder()
                .id(e.getId())
                .name(e.getName())
                .organizationName(e.getOrganizationName())
                .prospectType(e.getProspectType())
                .email(e.getEmail())
                .phone(e.getPhone())
                .secondaryContact(e.getSecondaryContact())
                .address(e.getAddress())
                .source(e.getSource())
                .interest(e.getInterest())
                .summary(e.getSummary())
                .pipelineStatus(e.getPipelineStatus())
                .relationshipStage(e.getRelationshipStage())
                .customerId(e.getCustomerId())
                .vendorId(e.getVendorId())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .notes(notes)
                .build();
    }

    private static String blankToNull(String value) {
        if (value == null) return null;
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }
}
