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
    private Long supplierId;
    private String supplierName;
    private LocalDate orderDate;
    private String status;
    private boolean archived;
    private BigDecimal totalAmount;
    private List<PurchaseOrderItemDTO> items;
    private String createdAt;
    private Long createdById;
    private String createdByName;
    /** True after vendor payment is confirmed in Accounts Payable. */
    private Boolean vendorPaymentSettled;
    /** ERP purchase invoice linked to this PO (for receipt after payment). */
    private Long purchaseInvoiceId;
    /** Vendor payable / payment row in AP (for payment receipt PDF). */
    private Long vendorPaymentId;
}
