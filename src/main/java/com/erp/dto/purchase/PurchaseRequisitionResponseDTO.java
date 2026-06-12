package com.erp.dto.purchase;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseRequisitionResponseDTO {

    private Long id;
    private String requisitionNumber;
    private String status;
    private Instant createdAt;
    private Instant approvedAt;
    private Instant convertedAt;

    private boolean archived;

    private Long preferredSupplierId;
    private String preferredSupplierName;
    private String supplierAddress;

    private Long departmentId;
    private String departmentName;

    private Long requestedById;
    private String requestedByName;

    private String rejectionReason;
    /** REJECT or SEND_BACK when returned to requester */
    private String reviewAction;
    private Instant rejectedAt;
    private Long rejectedById;
    private String rejectedByName;

    private LocalDate requestedDate;
    private LocalDate requiredDeliveryDate;
    private String projectCode;
    private String requisitionDescription;
    private String urgency;
    private Long deliveryWarehouseId;
    private String deliveryWarehouseName;
    private String justification;

    /** Populated when approval creates a purchase order in the same request. */
    private Long createdPurchaseOrderId;
    /** Human-readable PO number (e.g. PO-1000) when a linked order exists. */
    private String createdPurchaseOrderNumber;

    private Long debitAccountId;
    private String debitAccountName;
    private Long creditAccountId;
    private String creditAccountName;
    /** Posted finance transaction when this PR was approved. */
    private Long financeTransactionId;

    private List<PurchaseRequisitionItemDTO> items;
    private List<PurchaseRequisitionDocumentDTO> documents;
}
