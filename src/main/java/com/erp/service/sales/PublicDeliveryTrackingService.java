package com.erp.service.sales;

import com.erp.domain.hr.Company;
import com.erp.domain.sales.Shipment;
import com.erp.domain.sales.ShipmentItem;
import com.erp.domain.sales.ShipmentTrackingEvent;
import com.erp.dto.sales.PublicDeliveryTrackingCompanyDTO;
import com.erp.dto.sales.PublicDeliveryTrackingEventDTO;
import com.erp.dto.sales.PublicDeliveryTrackingItemDTO;
import com.erp.dto.sales.PublicDeliveryTrackingLookupRequest;
import com.erp.dto.sales.PublicDeliveryTrackingLookupResponse;
import com.erp.dto.sales.PublicDeliveryTrackingShipmentDTO;
import com.erp.repo.hr.CompanyRepository;
import com.erp.repo.sales.ShipmentRepository;
import com.erp.repo.sales.ShipmentTrackingEventRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class PublicDeliveryTrackingService {

    private static final Set<String> HIDDEN_STATUSES = Set.of("CANCELLED");
    private static final int RECENT_DELIVERED_DAYS = 30;

    private final CompanyRepository companyRepo;
    private final ShipmentRepository shipmentRepo;
    private final ShipmentTrackingEventRepository trackingEventRepo;

    public PublicDeliveryTrackingService(
            CompanyRepository companyRepo,
            ShipmentRepository shipmentRepo,
            ShipmentTrackingEventRepository trackingEventRepo
    ) {
        this.companyRepo = companyRepo;
        this.shipmentRepo = shipmentRepo;
        this.trackingEventRepo = trackingEventRepo;
    }

    public PublicDeliveryTrackingCompanyDTO getCompanyInfo(String companyCode) {
        Company company = resolveCompany(companyCode);
        return toCompanyDTO(company);
    }

    public PublicDeliveryTrackingLookupResponse lookup(
            String companyCode,
            PublicDeliveryTrackingLookupRequest request
    ) {
        Company company = resolveCompany(companyCode);
        validateLookupRequest(request);

        String orderNumber = normalizeText(request.getOrderNumber());
        String email = normalizeEmail(request.getEmail());
        String phoneDigits = digitsOnly(request.getPhone());

        List<PublicDeliveryTrackingShipmentDTO> deliveries = shipmentRepo
                .findByCompanyIdWithDetails(company.getId())
                .stream()
                .filter(this::isCustomerVisible)
                .filter(shipment -> matchesLookup(shipment, orderNumber, email, phoneDigits))
                .map(this::toShipmentDTO)
                .toList();

        return PublicDeliveryTrackingLookupResponse.builder()
                .company(toCompanyDTO(company))
                .deliveries(deliveries)
                .build();
    }

    private Company resolveCompany(String companyCode) {
        String normalizedCode = normalizeText(companyCode);
        if (normalizedCode == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Company code is required");
        }
        return companyRepo.findByCompanyCodeIgnoreCase(normalizedCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Company not found"));
    }

    private void validateLookupRequest(PublicDeliveryTrackingLookupRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lookup details are required");
        }
        boolean hasOrderNumber = normalizeText(request.getOrderNumber()) != null;
        boolean hasEmail = normalizeEmail(request.getEmail()) != null;
        boolean hasPhone = digitsOnly(request.getPhone()).length() >= 6;
        if (!hasOrderNumber && !hasEmail && !hasPhone) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Enter an order number, email, or phone number"
            );
        }
    }

    private boolean isCustomerVisible(Shipment shipment) {
        String status = normalizeStatus(shipment.getStatus());
        if (HIDDEN_STATUSES.contains(status)) {
            return false;
        }
        if (!"DELIVERED".equals(status)) {
            return true;
        }
        Instant deliveredAt = shipment.getDeliveredAt() != null
                ? shipment.getDeliveredAt()
                : shipment.getCreatedAt();
        if (deliveredAt == null) {
            return false;
        }
        return deliveredAt.isAfter(Instant.now().minus(RECENT_DELIVERED_DAYS, ChronoUnit.DAYS));
    }

    private boolean matchesLookup(
            Shipment shipment,
            String orderNumber,
            String email,
            String phoneDigits
    ) {
        boolean orderMatch = orderNumber == null || matchesOrderNumber(shipment, orderNumber);
        boolean emailMatch = email == null || matchesEmail(shipment, email);
        boolean phoneMatch = phoneDigits.isEmpty() || matchesPhone(shipment, phoneDigits);
        return orderMatch && emailMatch && phoneMatch;
    }

    private boolean matchesOrderNumber(Shipment shipment, String orderNumber) {
        String shipmentNumber = normalizeText(shipment.getShipmentNumber());
        String salesOrderNumber = normalizeText(shipment.getPicklist().getSalesOrder().getOrderNumber());
        String normalizedLookup = orderNumber.toUpperCase(Locale.ROOT);
        return Objects.equals(shipmentNumber, normalizedLookup)
                || Objects.equals(salesOrderNumber, normalizedLookup);
    }

    private boolean matchesEmail(Shipment shipment, String email) {
        String customerEmail = normalizeEmail(shipment.getCustomer().getEmail());
        return customerEmail != null && customerEmail.equals(email);
    }

    private boolean matchesPhone(Shipment shipment, String phoneDigits) {
        return phoneMatches(shipment.getCustomer().getPhoneNo(), phoneDigits)
                || phoneMatches(shipment.getCustomerPhone(), phoneDigits);
    }

    private boolean phoneMatches(String storedPhone, String queryDigits) {
        String storedDigits = digitsOnly(storedPhone);
        if (storedDigits.isEmpty() || queryDigits.isEmpty()) {
            return false;
        }
        if (storedDigits.equals(queryDigits) || storedDigits.contains(queryDigits) || queryDigits.contains(storedDigits)) {
            return true;
        }
        int suffixLength = Math.min(7, Math.min(storedDigits.length(), queryDigits.length()));
        if (suffixLength < 6) {
            return false;
        }
        String storedSuffix = storedDigits.substring(storedDigits.length() - suffixLength);
        String querySuffix = queryDigits.substring(queryDigits.length() - suffixLength);
        return storedSuffix.equals(querySuffix);
    }

    private PublicDeliveryTrackingCompanyDTO toCompanyDTO(Company company) {
        return PublicDeliveryTrackingCompanyDTO.builder()
                .companyCode(company.getCompanyCode())
                .companyName(company.getCompanyName())
                .logoUrl(company.getLogoUrl())
                .phoneNo(company.getPhoneNo())
                .companyEmail(company.getCompanyEmail())
                .websiteUrl(company.getWebsiteUrl())
                .build();
    }

    private PublicDeliveryTrackingShipmentDTO toShipmentDTO(Shipment shipment) {
        List<ShipmentTrackingEvent> events = trackingEventRepo
                .findByShipmentIdOrderByEventAtAscIdAsc(shipment.getId());
        return PublicDeliveryTrackingShipmentDTO.builder()
                .shipmentNumber(shipment.getShipmentNumber())
                .orderNumber(shipment.getPicklist().getSalesOrder().getOrderNumber())
                .status(publicStatus(shipment.getStatus()))
                .carrierName(shipment.getCarrierName())
                .trackingNumber(shipment.getTrackingNumber())
                .estimatedDeliveryDate(shipment.getEstimatedDeliveryDate())
                .deliveryAddress(shipment.getDeliveryAddress())
                .createdAt(shipment.getCreatedAt())
                .deliveredAt(shipment.getDeliveredAt())
                .items(shipment.getItems().stream().map(this::toItemDTO).toList())
                .trackingEvents(events.stream().map(this::toEventDTO).toList())
                .build();
    }

    private PublicDeliveryTrackingItemDTO toItemDTO(ShipmentItem item) {
        return PublicDeliveryTrackingItemDTO.builder()
                .itemName(item.getItem() != null ? item.getItem().getName() : null)
                .quantity(item.getQuantity())
                .build();
    }

    private PublicDeliveryTrackingEventDTO toEventDTO(ShipmentTrackingEvent event) {
        return PublicDeliveryTrackingEventDTO.builder()
                .status(publicStatus(event.getStatus()))
                .location(event.getLocation())
                .notes(sanitizePublicNotes(event.getNotes()))
                .eventAt(event.getEventAt())
                .build();
    }

    private String sanitizePublicNotes(String notes) {
        if (notes == null || notes.isBlank()) {
            return null;
        }
        return notes.trim();
    }

    private String publicStatus(String status) {
        String normalized = normalizeStatus(status);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private String normalizeStatus(String status) {
        if (status == null) {
            return null;
        }
        String trimmed = status.trim();
        return trimmed.isEmpty() ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    private String normalizeEmail(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim().toLowerCase(Locale.ROOT);
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String digitsOnly(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\D", "");
    }
}
