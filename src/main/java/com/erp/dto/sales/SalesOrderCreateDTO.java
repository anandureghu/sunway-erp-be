package com.erp.dto.sales;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalesOrderCreateDTO {
    @NotNull(message = "Customer is required")
    private Long customerId;

    @NotNull(message = "Order date is required")
    private LocalDate orderDate;

    @NotNull(message = "Invoice due date is required")
    private LocalDate invoiceDueDate;

    @NotNull(message = "Bank account is required")
    private Long bankAccountId;

    @NotNull(message = "Debit account is required")
    private Long debitAccountId;

    @NotNull(message = "Credit account is required")
    private Long creditAccountId;

    @Valid
    @NotEmpty(message = "At least one item is required")
    private List<SalesOrderItemDTO> items;
}
