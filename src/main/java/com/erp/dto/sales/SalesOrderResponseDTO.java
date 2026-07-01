package com.erp.dto.sales;

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
public class SalesOrderResponseDTO {

    private Long id;
    private String orderNumber;
    private Long customerId;
    private String customerName;
    private String customerEmail;
    private String customerPhone;
    private String customerAddress;
    private LocalDate orderDate;
    private LocalDate invoiceDueDate;
    private String shippingAddress;
    private String status;
    private boolean archived;
    private String paymentStatus;
    private BigDecimal subtotalAmount;
    private BigDecimal discountAmount;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private Long bankAccountId;
    private String bankAccountName;
    private Long debitAccountId;
    private String debitAccountName;
    private Long creditAccountId;
    private String creditAccountName;
    /** Current balance on the sales debit account (draft confirmation check). */
    private BigDecimal debitAccountBalance;
    /** False when draft order total exceeds available debit account balance. */
    private Boolean sufficientDebitBalance;
    /** Amount short on debit account when {@link #sufficientDebitBalance} is false. */
    private BigDecimal debitBalanceShortage;
    private List<SalesOrderItemResponseDTO> items;
    /** Linked sales invoice (created when the order is confirmed). */
    private Long salesInvoiceId;
}
