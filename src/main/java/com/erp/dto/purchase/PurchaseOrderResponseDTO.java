package com.erp.dto.purchase;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderResponseDTO {
    private Long id;
    private String orderNumber;
    private Long sourceRequisitionId;
    /** Human-readable PR number (e.g. PR-1000) when sourced from a requisition. */
    private String sourceRequisitionNumber;
    private Long supplierId;
    private String supplierName;
    private LocalDate orderDate;
    private LocalDate requiredDeliveryDate;
    private String status;
    private boolean archived;
    private BigDecimal totalAmount;
    private List<PurchaseOrderItemDTO> items;
    private String createdAt;
    private Long createdById;
    private String createdByName;
    private Long requestedById;
    private String requestedByName;
    /** True after vendor payment is confirmed in Accounts Payable. */
    private Boolean vendorPaymentSettled;
    /**
     * Payment status from the linked purchase invoice (UNPAID, PARTIALLY_PAID, PAID, …).
     * Defaults to UNPAID when no purchase invoice exists yet.
     */
    private String paymentStatus;
<<<<<<< Updated upstream
    /** Remaining balance on the linked purchase invoice, when partially paid. */
    private BigDecimal outstandingAmount;
=======
>>>>>>> Stashed changes
    /** ERP purchase invoice linked to this PO (for receipt after payment). */
    private Long purchaseInvoiceId;
    /** Vendor payable / payment row in AP (for payment receipt PDF). */
    private Long vendorPaymentId;
}
