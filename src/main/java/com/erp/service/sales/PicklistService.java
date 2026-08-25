package com.erp.service.sales;

import com.erp.domain.InvoiceType;
import com.erp.domain.User;
import com.erp.domain.hr.Company;
import com.erp.domain.inventory.Warehouse;
import com.erp.domain.sales.Picklist;
import com.erp.domain.sales.PicklistItem;
import com.erp.domain.sales.SalesOrder;
import com.erp.domain.sales.SalesOrderItem;
import com.erp.domain.sales.Shipment;
import com.erp.repo.finance.InvoiceRepository;
import com.erp.dto.sales.PicklistItemDTO;
import com.erp.dto.sales.PicklistResponseDTO;
import com.erp.exception.ConflictException;
import com.erp.repo.UserRepository;
import com.erp.repo.hr.CompanyRepository;
import com.erp.repo.inventory.WarehouseRepository;
import com.erp.repo.sales.PicklistRepository;
import com.erp.repo.sales.SalesOrderRepository;
import com.erp.repo.sales.ShipmentRepository;
import com.erp.security.context.AuthContext;
import com.erp.service.DocumentSequenceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class PicklistService {

    private final PicklistRepository repo;
    private final SalesOrderRepository soRepo;
    private final InvoiceRepository invoiceRepo;
    private final ShipmentRepository shipmentRepo;
    private final WarehouseRepository warehouseRepo;
    private final CompanyRepository companyRepo;
    private final UserRepository userRepo;
    private final AuthContext auth;
    private final DocumentSequenceService documentSequenceService;

    public PicklistService(
            PicklistRepository repo,
            SalesOrderRepository soRepo,
            InvoiceRepository invoiceRepo,
            ShipmentRepository shipmentRepo,
            WarehouseRepository warehouseRepo,
            CompanyRepository companyRepo,
            UserRepository userRepo,
            AuthContext auth,
            DocumentSequenceService documentSequenceService
    ) {
        this.repo = repo;
        this.soRepo = soRepo;
        this.invoiceRepo = invoiceRepo;
        this.shipmentRepo = shipmentRepo;
        this.warehouseRepo = warehouseRepo;
        this.companyRepo = companyRepo;
        this.userRepo = userRepo;
        this.auth = auth;
        this.documentSequenceService = documentSequenceService;
    }

    // --------------------------
    // Generate Picklist
    // --------------------------
    public PicklistResponseDTO generate(Long salesOrderId) {
        return generate(salesOrderId, null);
    }

    public PicklistResponseDTO generate(Long salesOrderId, Long warehouseId) {
        Long companyId = auth.getCurrentCompanyId();

        SalesOrder so = soRepo.findById(salesOrderId)
                .filter(o -> o.getCompany().getId().equals(companyId))
                .orElseThrow(() -> new RuntimeException("Sales order not found"));

        if (!"CONFIRMED".equals(so.getStatus())) {
            throw new RuntimeException("Picklist can be generated only for confirmed sales orders");
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

        Warehouse filterWarehouse = null;
        if (warehouseId != null) {
            filterWarehouse = warehouseRepo.findById(warehouseId)
                    .filter(w -> w.getCompany().getId().equals(companyId))
                    .orElseThrow(() -> new RuntimeException("Warehouse not found"));
        }

        final Long filterWhId = filterWarehouse != null ? filterWarehouse.getId() : null;
        List<SalesOrderItem> sourceLines = so.getItems().stream()
                .filter(line -> filterWhId == null || filterWhId.equals(resolveLineWarehouseId(line)))
                .collect(Collectors.toList());
        if (sourceLines.isEmpty()) {
            throw new RuntimeException(
                    filterWhId == null
                            ? "Cannot generate picklist: sales order has no items"
                            : "No sales order lines match the selected warehouse"
            );
        }

        Company company = companyRepo.findById(companyId).orElseThrow();
        User user = userRepo.findById(auth.getCurrentUserId()).orElseThrow();

        List<PicklistItem> items = sourceLines.stream()
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

        return toDTO(repo.save(picklist), filterWarehouse);
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
        if (!"CREATED".equals(p.getStatus())) {
            throw new RuntimeException("Only CREATED picklists can be cancelled");
        }
        if (shipmentRepo.findByPicklistId(p.getId()).isPresent()) {
            throw new ConflictException("Cannot cancel picklist: a shipment already exists");
        }

        p.setStatus("CANCELLED");
        return toDTO(repo.save(p));
    }

    // --------------------------
    // Archive
    // --------------------------
    public PicklistResponseDTO archive(Long id) {
        Picklist p = getEntity(id);
        if (p.isArchived()) {
            return toDTO(p);
        }
        if (!"PICKED".equals(p.getStatus()) && !"CANCELLED".equals(p.getStatus())) {
            throw new ConflictException("Only picked or cancelled picklists can be archived");
        }
        shipmentRepo.findByPicklistId(p.getId()).ifPresent(sh -> {
            if (!List.of("DELIVERED", "CANCELLED").contains(sh.getStatus())) {
                throw new ConflictException(
                        "Cannot archive picklist while shipment is still active (" + sh.getStatus() + ")");
            }
        });
        p.setArchived(true);
        return toDTO(repo.save(p));
    }

    // --------------------------
    // Get / List
    // --------------------------
    public PicklistResponseDTO get(Long id) {
        return toDTO(getEntity(id));
    }

    public List<PicklistResponseDTO> list() {
        return repo.findByCompanyIdOrderByCreatedAtDesc(auth.getCurrentCompanyId())
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
        return documentSequenceService.generateNext("PL");
    }

    private PicklistResponseDTO toDTO(Picklist p) {
        return toDTO(p, null);
    }

    private PicklistResponseDTO toDTO(Picklist p, Warehouse preferredWarehouse) {
        Long warehouseId = preferredWarehouse != null ? preferredWarehouse.getId() : null;
        String warehouseName = preferredWarehouse != null ? preferredWarehouse.getName() : null;
        SalesOrder salesOrder = p.getSalesOrder();
        if (warehouseId == null && salesOrder != null && salesOrder.getItems() != null && !salesOrder.getItems().isEmpty()) {
            SalesOrderItem line = salesOrder.getItems().get(0);
            warehouseId = resolveLineWarehouseId(line);
            warehouseName = resolveLineWarehouseName(line);
        }

        Long shipmentId = shipmentRepo.findByPicklistId(p.getId())
                .map(Shipment::getId)
                .orElse(null);

        return PicklistResponseDTO.builder()
                .id(p.getId())
                .picklistNumber(p.getPicklistNumber())
                .salesOrderId(p.getSalesOrder().getId())
                .status(p.getStatus())
                .archived(p.isArchived())
                .createdAt(p.getCreatedAt())
                .warehouseId(warehouseId)
                .warehouseName(warehouseName)
                .shipmentId(shipmentId)
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

    private Long resolveLineWarehouseId(SalesOrderItem line) {
        if (line.getWarehouse() != null) {
            return line.getWarehouse().getId();
        }
        if (line.getItem() != null && line.getItem().getWarehouse() != null) {
            return line.getItem().getWarehouse().getId();
        }
        return null;
    }

    private String resolveLineWarehouseName(SalesOrderItem line) {
        if (line.getWarehouse() != null) {
            return line.getWarehouse().getName();
        }
        if (line.getItem() != null && line.getItem().getWarehouse() != null) {
            return line.getItem().getWarehouse().getName();
        }
        return null;
    }
}
