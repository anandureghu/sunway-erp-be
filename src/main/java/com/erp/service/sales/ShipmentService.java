package com.erp.service.sales;

import com.erp.domain.User;
import com.erp.domain.hr.Company;
import com.erp.domain.sales.Picklist;
import com.erp.domain.sales.Shipment;
import com.erp.domain.sales.ShipmentItem;
import com.erp.dto.sales.ShipmentCreateDTO;
import com.erp.dto.sales.ShipmentItemDTO;
import com.erp.dto.sales.ShipmentResponseDTO;
import com.erp.repo.UserRepository;
import com.erp.repo.hr.CompanyRepository;
import com.erp.repo.sales.PicklistRepository;
import com.erp.repo.sales.ShipmentRepository;
import com.erp.security.context.AuthContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@Transactional
public class ShipmentService {

    private final ShipmentRepository repo;
    private final PicklistRepository picklistRepo;
    private final CompanyRepository companyRepo;
    private final UserRepository userRepo;
    private final AuthContext auth;

    public ShipmentService(
            ShipmentRepository repo,
            PicklistRepository picklistRepo,
            CompanyRepository companyRepo,
            UserRepository userRepo,
            AuthContext auth
    ) {
        this.repo = repo;
        this.picklistRepo = picklistRepo;
        this.companyRepo = companyRepo;
        this.userRepo = userRepo;
        this.auth = auth;
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

        if (repo.findByPicklistId(picklist.getId()).isPresent()) {
            throw new RuntimeException("Shipment already exists for this picklist");
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
                .company(company)
                .createdByUser(user)
                .items(items)
                .build();

        return toDTO(repo.save(shipment));
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
        s.setDispatchedAt(Instant.now());

        // 🔥 Stock OUT will be added here later

        return toDTO(repo.save(s));
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
        return toDTO(repo.save(s));
    }

    // --------------------------
    // Mark Delivered
    // --------------------------
    public ShipmentResponseDTO markDelivered(Long id) {

        Shipment s = getEntity(id);

        if (!List.of("DISPATCHED", "IN_TRANSIT").contains(s.getStatus())) {
            throw new RuntimeException("Shipment cannot be delivered in current state");
        }

        s.setStatus("DELIVERED");
        s.setDeliveredAt(Instant.now());

        return toDTO(repo.save(s));
    }

    // --------------------------
    // Cancel
    // --------------------------
    public ShipmentResponseDTO cancel(Long id) {

        Shipment s = getEntity(id);

        if (!"CREATED".equals(s.getStatus())) {
            throw new RuntimeException("Only CREATED shipments can be cancelled");
        }

        s.setStatus("CANCELLED");
        return toDTO(repo.save(s));
    }

    // --------------------------
    // Get / List
    // --------------------------
    public ShipmentResponseDTO get(Long id) {
        return toDTO(getEntity(id));
    }

    public List<ShipmentResponseDTO> list() {
        return repo.findByCompanyId(auth.getCurrentCompanyId())
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
        return "SH-" + System.currentTimeMillis();
    }

    private ShipmentResponseDTO toDTO(Shipment s) {
        return ShipmentResponseDTO.builder()
                .id(s.getId())
                .shipmentNumber(s.getShipmentNumber())
                .picklistId(s.getPicklist().getId())
                .customerId(s.getCustomer().getId())
                .status(s.getStatus())
                .carrierName(s.getCarrierName())
                .trackingNumber(s.getTrackingNumber())
                .dispatchedAt(s.getDispatchedAt())
                .deliveredAt(s.getDeliveredAt())
                .items(
                        s.getItems().stream()
                                .map(i -> ShipmentItemDTO.builder()
                                        .itemId(i.getItem().getId())
                                        .quantity(i.getQuantity())
                                        .build())
                                .toList()
                )
                .build();
    }
}
