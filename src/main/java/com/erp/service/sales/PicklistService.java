package com.erp.service.sales;

import com.erp.domain.InvoiceType;
import com.erp.domain.User;
import com.erp.domain.hr.Company;
import com.erp.domain.sales.Picklist;
import com.erp.domain.sales.PicklistItem;
import com.erp.domain.sales.SalesOrder;
import com.erp.repo.finance.InvoiceRepository;
import com.erp.dto.sales.PicklistItemDTO;
import com.erp.dto.sales.PicklistResponseDTO;
import com.erp.repo.UserRepository;
import com.erp.repo.hr.CompanyRepository;
import com.erp.repo.sales.PicklistRepository;
import com.erp.repo.sales.SalesOrderRepository;
import com.erp.security.context.AuthContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class PicklistService {

    private final PicklistRepository repo;
    private final SalesOrderRepository soRepo;
    private final InvoiceRepository invoiceRepo;
    private final CompanyRepository companyRepo;
    private final UserRepository userRepo;
    private final AuthContext auth;

    public PicklistService(
            PicklistRepository repo,
            SalesOrderRepository soRepo,
            InvoiceRepository invoiceRepo,
            CompanyRepository companyRepo,
            UserRepository userRepo,
            AuthContext auth
    ) {
        this.repo = repo;
        this.soRepo = soRepo;
        this.invoiceRepo = invoiceRepo;
        this.companyRepo = companyRepo;
        this.userRepo = userRepo;
        this.auth = auth;
    }

    // --------------------------
    // Generate Picklist
    // --------------------------
    public PicklistResponseDTO generate(Long salesOrderId) {
        Long companyId = auth.getCurrentCompanyId();

        SalesOrder so = soRepo.findById(salesOrderId)
                .filter(o -> o.getCompany().getId().equals(companyId))
                .orElseThrow(() -> new RuntimeException("Sales order not found"));

        if (!"CONFIRMED".equals(so.getStatus())) {
            throw new RuntimeException("Picklist can be generated only for CONFIRMED sales orders");
        }
        var invoice = invoiceRepo.findByOrderIdAndType(so.getId(), InvoiceType.SALES)
                .orElseThrow(() -> new RuntimeException("Invoice not found for this sales order"));
        if (!"PAID".equalsIgnoreCase(invoice.getStatus())) {
            throw new RuntimeException("Picklist can be generated only after full customer payment");
        }

        if (repo.findByCompanyIdAndSalesOrderId(companyId, so.getId()).isPresent()) {
            throw new RuntimeException("Picklist already exists for this sales order");
        }

        if (so.getItems() == null || so.getItems().isEmpty()) {
            throw new RuntimeException("Cannot generate picklist: sales order has no items");
        }

        Company company = companyRepo.findById(companyId).orElseThrow();
        User user = userRepo.findById(auth.getCurrentUserId()).orElseThrow();

        List<PicklistItem> items = so.getItems().stream()
                .map(i -> PicklistItem.builder()
                        .item(i.getItem())
                        .quantity(i.getQuantity())
                        .build()
                ).toList();

        Picklist picklist = Picklist.builder()
                .picklistNumber(generatePicklistNumber())
                .salesOrder(so)
                .status("CREATED")
                .company(company)
                .createdByUser(user)
                .items(items)
                .build();

        return toDTO(repo.save(picklist));
    }

    // --------------------------
    // Mark as PICKED
    // --------------------------
    public PicklistResponseDTO markPicked(Long id) {

        Picklist p = getEntity(id);

        if (!"CREATED".equals(p.getStatus())) {
            throw new RuntimeException("Only CREATED picklists can be marked as PICKED");
        }

        p.setStatus("PICKED");
        return toDTO(repo.save(p));
    }

    // --------------------------
    // Cancel Picklist
    // --------------------------
    public PicklistResponseDTO cancel(Long id) {

        Picklist p = getEntity(id);

        if ("CANCELLED".equals(p.getStatus())) {
            throw new RuntimeException("Picklist already cancelled");
        }

        p.setStatus("CANCELLED");
        return toDTO(repo.save(p));
    }

    // --------------------------
    // Get / List
    // --------------------------
    public PicklistResponseDTO get(Long id) {
        return toDTO(getEntity(id));
    }

    public List<PicklistResponseDTO> list() {
        return repo.findByCompanyId(auth.getCurrentCompanyId())
                .stream().map(this::toDTO).toList();
    }

    // --------------------------
    // Helpers
    // --------------------------
    private Picklist getEntity(Long id) {
        return repo.findById(id)
                .filter(p -> p.getCompany().getId().equals(auth.getCurrentCompanyId()))
                .orElseThrow(() -> new RuntimeException("Picklist not found or access denied"));
    }

    private String generatePicklistNumber() {
        return "PL-" + System.currentTimeMillis();
    }

    private PicklistResponseDTO toDTO(Picklist p) {
        return PicklistResponseDTO.builder()
                .id(p.getId())
                .picklistNumber(p.getPicklistNumber())
                .salesOrderId(p.getSalesOrder().getId())
                .status(p.getStatus())
                .createdAt(p.getCreatedAt())
                .items(
                        p.getItems().stream()
                                .map(i -> PicklistItemDTO.builder()
                                        .itemId(i.getItem().getId())
                                        .itemName(i.getItem().getName())
                                        .quantity(i.getQuantity())
                                        .build())
                                .toList()
                )
                .build();
    }
}
