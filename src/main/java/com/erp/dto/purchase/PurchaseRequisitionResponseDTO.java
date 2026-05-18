package com.erp.dto.purchase;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
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

    /** Populated when approval creates a purchase order in the same request. */
    private Long createdPurchaseOrderId;

    private Long debitAccountId;
    private String debitAccountName;
    private Long creditAccountId;
    private String creditAccountName;
    /** Posted finance transaction when this PR was approved. */
    private Long financeTransactionId;

    private List<PurchaseRequisitionItemDTO> items;
}
