package com.erp.service.sales;

import com.erp.domain.InvoiceType;
import com.erp.domain.User;
import com.erp.domain.hr.Company;
import com.erp.domain.inventory.Item;
import com.erp.domain.sales.Picklist;
import com.erp.domain.sales.SalesOrder;
import com.erp.domain.sales.Shipment;
import com.erp.domain.sales.ShipmentItem;
import com.erp.domain.sales.ShipmentTrackingEvent;
import com.erp.dto.sales.ShipmentCreateDTO;
import com.erp.dto.sales.ShipmentDeliverDTO;
import com.erp.dto.sales.ShipmentItemDTO;
import com.erp.dto.sales.ShipmentResponseDTO;
import com.erp.dto.sales.ShipmentTrackingEventCreateDTO;
import com.erp.dto.sales.ShipmentTrackingEventDTO;
import com.erp.repo.UserRepository;
import com.erp.repo.finance.InvoiceRepository;
import com.erp.repo.hr.CompanyRepository;
import com.erp.repo.sales.PicklistRepository;
import com.erp.repo.sales.SalesOrderRepository;
import com.erp.repo.sales.ShipmentRepository;
import com.erp.repo.sales.ShipmentTrackingEventRepository;
import com.erp.security.context.AuthContext;
import com.erp.service.DocumentSequenceService;
import com.erp.exception.ConflictException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@Transactional
public class ShipmentService {

    private final ShipmentRepository repo;
    private final PicklistRepository picklistRepo;
    private final SalesOrderRepository salesOrderRepo;
    private final InvoiceRepository invoiceRepo;
    private final CompanyRepository companyRepo;
    private final UserRepository userRepo;
    private final ShipmentTrackingEventRepository trackingEventRepo;
    private final AuthContext auth;
    private final DocumentSequenceService documentSequenceService;

    public ShipmentService(
            ShipmentRepository repo,
            PicklistRepository picklistRepo,
            SalesOrderRepository salesOrderRepo,
            InvoiceRepository invoiceRepo,
            CompanyRepository companyRepo,
            UserRepository userRepo,
            ShipmentTrackingEventRepository trackingEventRepo,
            AuthContext auth,
            DocumentSequenceService documentSequenceService
    ) {
        this.repo = repo;
        this.picklistRepo = picklistRepo;
        this.salesOrderRepo = salesOrderRepo;
        this.invoiceRepo = invoiceRepo;
        this.companyRepo = companyRepo;
        this.userRepo = userRepo;
        this.trackingEventRepo = trackingEventRepo;
        this.auth = auth;
        this.documentSequenceService = documentSequenceService;
    }

    // --------------------------
    // Create Shipment
    // --------------------------
    public ShipmentResponseDTO create(Long picklistId, ShipmentCreateDTO dto) {

        Picklist picklist = picklistRepo.findById(picklistId)
                .filter(p -> p.getCompany().getId().equals(auth.getCurrentCompanyId()))
                .orElseThrow(() -> new RuntimeException("Picklist not found"));

        if (!"PICKED".equals(picklist.getStatus())) {
            throw new RuntimeException("Shipment can be created only from PICKED picklist");
        }
        SalesOrder salesOrder = picklist.getSalesOrder();
        if (salesOrder == null) {
            throw new RuntimeException("Picklist is not linked to a sales order");
        }
        if ("COMPLETED".equals(salesOrder.getStatus()) || "CANCELLED".equals(salesOrder.getStatus())) {
            throw new ConflictException(
                    "Cannot create dispatch: sales order is already "
                            + salesOrder.getStatus().toLowerCase());
        }
        var invoice = invoiceRepo.findByOrderIdAndType(salesOrder.getId(), InvoiceType.SALES)
                .orElseThrow(() -> new RuntimeException("Invoice not found for this sales order"));
        if (!"PAID".equalsIgnoreCase(invoice.getStatus())) {
            throw new RuntimeException("Shipment can be created only after full customer payment");
        }

        if (repo.findByPicklistId(picklist.getId()).isPresent()) {
            throw new RuntimeException("Shipment already exists for this picklist");
        }
        if (repo.existsDeliveredForSalesOrder(salesOrder.getId())) {
            throw new ConflictException(
                    "Cannot create dispatch: this sales order was already delivered");
        }

        Company company = companyRepo.findById(auth.getCurrentCompanyId()).orElseThrow();
        User user = userRepo.findById(auth.getCurrentUserId()).orElseThrow();

        List<ShipmentItem> items = picklist.getItems().stream()
                .map(i -> ShipmentItem.builder()
                        .item(i.getItem())
                        .quantity(i.getQuantity())
                        .build())
                .toList();

        Shipment shipment = Shipment.builder()
                .shipmentNumber(generateShipmentNumber())
                .picklist(picklist)
                .customer(picklist.getSalesOrder().getCustomer())
                .status("CREATED")
                .carrierName(dto.getCarrierName())
                .trackingNumber(dto.getTrackingNumber())
                .vehicleNumber(dto.getVehicleNumber())
                .driverName(dto.getDriverName())
                .driverPhone(dto.getDriverPhone())
                .customerPhone(dto.getCustomerPhone())
                .estimatedDeliveryDate(dto.getEstimatedDeliveryDate())
                .deliveryAddress(resolveDeliveryAddress(dto.getDeliveryAddress(), picklist))
                .notes(dto.getNotes())
                .company(company)
                .createdByUser(user)
                .items(items)
                .trackingEvents(new ArrayList<>())
                .build();

        Shipment saved = repo.save(shipment);
        appendTrackingEvent(saved, "CREATED", "Origin", "Shipment created", Instant.now());
        return toDTO(saved);
    }

    // --------------------------
    // Dispatch
    // --------------------------
    public ShipmentResponseDTO dispatch(Long id) {

        Shipment s = getEntity(id);

        if (!"CREATED".equals(s.getStatus())) {
            throw new RuntimeException("Only CREATED shipments can be dispatched");
        }

        s.setStatus("DISPATCHED");
        Instant now = Instant.now();
        s.setDispatchedAt(now);
        // Stock was already FIFO-consumed when the sales order was confirmed.
        // Do not rewrite item reserved/quantity aggregates here.

        appendTrackingEvent(s, "DISPATCHED", "Origin Dispatch Center", "Shipment dispatched", now);
        return toDTO(s);
    }

    // --------------------------
    // Mark In Transit
    // --------------------------
    public ShipmentResponseDTO markInTransit(Long id) {

        Shipment s = getEntity(id);

        if (!"DISPATCHED".equals(s.getStatus())) {
            throw new RuntimeException("Shipment must be DISPATCHED first");
        }

        s.setStatus("IN_TRANSIT");
        Instant now = Instant.now();
        s.setInTransitAt(now);
        appendTrackingEvent(s, "IN_TRANSIT", "In transit", "Shipment is in transit", now);
        return toDTO(s);
    }

    public ShipmentResponseDTO markOutForDelivery(Long id) {
        Shipment s = getEntity(id);

        String previous = s.getStatus();
        // IN_TRANSIT is the normal path; FAILED_DELIVERY allows a reattempt.
        if (!List.of("IN_TRANSIT", "FAILED_DELIVERY").contains(previous)) {
            throw new RuntimeException("Shipment must be IN_TRANSIT (or FAILED_DELIVERY to reattempt)");
        }

        s.setStatus("OUT_FOR_DELIVERY");
        Instant now = Instant.now();
        s.setOutForDeliveryAt(now);
        String note = "FAILED_DELIVERY".equals(previous)
                ? "Reattempting delivery after failed attempt"
                : "Shipment is out for delivery";
        appendTrackingEvent(s, "OUT_FOR_DELIVERY", resolveEventLocation(s), note, now);
        return toDTO(s);
    }

    // --------------------------
    // Mark Delivered (proof of delivery)
    // --------------------------
    public ShipmentResponseDTO markDelivered(Long id, ShipmentDeliverDTO dto) {

        Shipment s = getEntity(id);

        if (!List.of("DISPATCHED", "IN_TRANSIT", "OUT_FOR_DELIVERY", "FAILED_DELIVERY").contains(s.getStatus())) {
            throw new RuntimeException("Shipment cannot be delivered in current state");
        }

        String signature = dto != null ? blankToNull(dto.getCustomerSignature()) : null;
        String remarks = dto != null ? blankToNull(dto.getDeliveryRemarks()) : null;
        if (signature == null) {
            throw new RuntimeException("Customer signature is required to mark delivered");
        }

        s.setCustomerSignature(signature);
        s.setDeliveryRemarks(remarks);
        s.setStatus("DELIVERED");
        Instant now = Instant.now();
        s.setDeliveredAt(now);
        String eventNotes = remarks != null ? remarks : "Shipment delivered with customer signature";
        appendTrackingEvent(s, "DELIVERED", resolveEventLocation(s), eventNotes, now);

        SalesOrder linkedOrder = s.getPicklist().getSalesOrder();
        if (linkedOrder != null && !"CANCELLED".equals(linkedOrder.getStatus())) {
            linkedOrder.setStatus("COMPLETED");
            salesOrderRepo.save(linkedOrder);
        }

        return toDTO(s);
    }

    public ShipmentResponseDTO markFailedDelivery(Long id, String notes) {
        Shipment s = getEntity(id);

        if (!List.of("DISPATCHED", "IN_TRANSIT", "OUT_FOR_DELIVERY").contains(s.getStatus())) {
            throw new RuntimeException("Shipment cannot be marked failed in current state");
        }

        s.setStatus("FAILED_DELIVERY");
        Instant now = Instant.now();
        s.setFailedDeliveryAt(now);
        appendTrackingEvent(s, "FAILED_DELIVERY", resolveEventLocation(s), notes, now);
        return toDTO(s);
    }

    public ShipmentResponseDTO addTrackingUpdate(Long id, ShipmentTrackingEventCreateDTO dto) {
        Shipment s = getEntity(id);
        String status = normalizeStatus(dto.getStatus());
        if (status != null && !status.isBlank() && !status.equals(s.getStatus())) {
            throw new RuntimeException("Tracking update status must match current shipment status");
        }
        appendTrackingEvent(
                s,
                s.getStatus(),
                dto.getLocation(),
                dto.getNotes(),
                dto.getEventAt() == null ? Instant.now() : dto.getEventAt()
        );
        return toDTO(s);
    }

    public ShipmentResponseDTO updateDetails(Long id, ShipmentCreateDTO dto) {
        Shipment s = getEntity(id);

        if (dto.getCarrierName() != null) s.setCarrierName(blankToNull(dto.getCarrierName()));
        if (dto.getTrackingNumber() != null) s.setTrackingNumber(blankToNull(dto.getTrackingNumber()));
        if (dto.getVehicleNumber() != null) s.setVehicleNumber(blankToNull(dto.getVehicleNumber()));
        if (dto.getDriverName() != null) s.setDriverName(blankToNull(dto.getDriverName()));
        if (dto.getDriverPhone() != null) s.setDriverPhone(blankToNull(dto.getDriverPhone()));
        if (dto.getCustomerPhone() != null) s.setCustomerPhone(blankToNull(dto.getCustomerPhone()));
        if (dto.getEstimatedDeliveryDate() != null) s.setEstimatedDeliveryDate(blankToNull(dto.getEstimatedDeliveryDate()));
        if (dto.getDeliveryAddress() != null) s.setDeliveryAddress(blankToNull(dto.getDeliveryAddress()));
        if (dto.getNotes() != null) s.setNotes(blankToNull(dto.getNotes()));

        return toDTO(s);
    }

    // --------------------------
    // Cancel
    // --------------------------
    public ShipmentResponseDTO cancel(Long id) {

        Shipment s = getEntity(id);

        if (List.of("DELIVERED", "CANCELLED").contains(s.getStatus())) {
            throw new RuntimeException("Delivered/cancelled shipments cannot be cancelled");
        }

        s.setStatus("CANCELLED");
        appendTrackingEvent(s, "CANCELLED", resolveEventLocation(s), "Shipment cancelled", Instant.now());
        return toDTO(s);
    }

    // --------------------------
    // Get / List
    // --------------------------
    public ShipmentResponseDTO get(Long id) {
        return toDTO(getEntity(id));
    }

    public List<ShipmentResponseDTO> list() {
        return repo.findByCompanyIdOrderByCreatedAtDesc(auth.getCurrentCompanyId())
                .stream().map(this::toDTO).toList();
    }

    // --------------------------
    // Helpers
    // --------------------------
    private Shipment getEntity(Long id) {
        return repo.findById(id)
                .filter(s -> s.getCompany().getId().equals(auth.getCurrentCompanyId()))
                .orElseThrow(() -> new RuntimeException("Shipment not found or access denied"));
    }

    private String generateShipmentNumber() {
        return documentSequenceService.generateNext("SH");
    }

    private String resolveDeliveryAddress(String dtoAddress, Picklist picklist) {
        if (dtoAddress != null && !dtoAddress.isBlank()) {
            return dtoAddress.trim();
        }
        String orderAddress = picklist.getSalesOrder().getShippingAddress();
        if (orderAddress != null && !orderAddress.isBlank()) {
            return orderAddress.trim();
        }
        return null;
    }

    private String resolveEventLocation(Shipment shipment) {
        if (shipment.getDeliveryAddress() != null && !shipment.getDeliveryAddress().isBlank()) {
            return shipment.getDeliveryAddress();
        }
        return "Unknown";
    }

    private ShipmentResponseDTO toDTO(Shipment s) {
        List<ShipmentTrackingEvent> events = trackingEventRepo.findByShipmentIdOrderByEventAtAscIdAsc(s.getId());
        return ShipmentResponseDTO.builder()
                .id(s.getId())
                .shipmentNumber(s.getShipmentNumber())
                .picklistId(s.getPicklist().getId())
                .customerId(s.getCustomer().getId())
                .status(s.getStatus())
                .carrierName(s.getCarrierName())
                .trackingNumber(s.getTrackingNumber())
                .vehicleNumber(s.getVehicleNumber())
                .driverName(s.getDriverName())
                .driverPhone(s.getDriverPhone())
                .customerPhone(s.getCustomerPhone())
                .estimatedDeliveryDate(s.getEstimatedDeliveryDate())
                .deliveryAddress(s.getDeliveryAddress())
                .notes(s.getNotes())
                .customerSignature(s.getCustomerSignature())
                .deliveryRemarks(s.getDeliveryRemarks())
                .createdAt(s.getCreatedAt())
                .dispatchedAt(s.getDispatchedAt())
                .inTransitAt(s.getInTransitAt())
                .outForDeliveryAt(s.getOutForDeliveryAt())
                .deliveredAt(s.getDeliveredAt())
                .failedDeliveryAt(s.getFailedDeliveryAt())
                .items(
                        s.getItems().stream()
                                .map(i -> ShipmentItemDTO.builder()
                                        .itemId(i.getItem().getId())
                                        .quantity(i.getQuantity())
                                        .build())
                                .toList()
                )
                .trackingEvents(events.stream().map(this::toEventDTO).toList())
                .build();
    }

    private void appendTrackingEvent(Shipment shipment, String status, String location, String notes, Instant eventAt) {
        User user = userRepo.findById(auth.getCurrentUserId()).orElseThrow();
        trackingEventRepo.save(ShipmentTrackingEvent.builder()
                .shipment(shipment)
                .status(status)
                .location(location)
                .notes(notes)
                .eventAt(eventAt)
                .createdByUser(user)
                .build());
    }

    private ShipmentTrackingEventDTO toEventDTO(ShipmentTrackingEvent event) {
        return ShipmentTrackingEventDTO.builder()
                .id(event.getId())
                .status(event.getStatus())
                .location(event.getLocation())
                .notes(event.getNotes())
                .eventAt(event.getEventAt())
                .createdByUserId(event.getCreatedByUser() != null ? event.getCreatedByUser().getId() : null)
                .build();
    }

    private String normalizeStatus(String status) {
        if (status == null) return null;
        return status.trim().toUpperCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
