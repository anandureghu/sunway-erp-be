package com.erp.service.sales;

import com.erp.domain.InvoiceType;
import com.erp.domain.User;
import com.erp.domain.finance.CreditNote;
import com.erp.domain.finance.Invoice;
import com.erp.domain.hr.Company;
import com.erp.domain.inventory.StockBatchSourceType;
import com.erp.domain.inventory.Warehouse;
import com.erp.domain.sales.SalesOrder;
import com.erp.domain.sales.SalesOrderItem;
import com.erp.domain.sales.SalesReturn;
import com.erp.domain.sales.SalesReturnItem;
import com.erp.dto.finance.CreditNoteResponseDTO;
import com.erp.dto.sales.CreateSalesReturnDTO;
import com.erp.dto.sales.SalesReturnResponseDTO;
import com.erp.exception.ConflictException;
import com.erp.exception.NotFoundException;
import com.erp.repo.UserRepository;
import com.erp.repo.finance.CreditNoteRepository;
import com.erp.repo.finance.InvoiceRepository;
import com.erp.repo.sales.SalesOrderRepository;
import com.erp.repo.sales.SalesReturnRepository;
import com.erp.security.context.AuthContext;
import com.erp.service.DocumentSequenceService;
import com.erp.service.finance.CreditNoteService;
import com.erp.service.finance.InvoiceService;
import com.erp.service.inventory.StockBatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class SalesReturnService {

    private final SalesReturnRepository salesReturnRepo;
    private final SalesOrderRepository salesOrderRepo;
    private final InvoiceRepository invoiceRepo;
    private final CreditNoteRepository creditNoteRepo;
    private final UserRepository userRepo;
    private final AuthContext auth;
    private final DocumentSequenceService documentSequenceService;
    private final InvoiceService invoiceService;
    private final CreditNoteService creditNoteService;
    private final StockBatchService stockBatchService;

    public List<SalesReturnResponseDTO> listForCompany() {
        return salesReturnRepo.findByCompanyIdOrderByCreatedAtDesc(auth.getCurrentCompanyId())
                .stream()
                .map(this::toDTO)
                .toList();
    }

    public List<SalesReturnResponseDTO> listForSalesOrder(Long salesOrderId) {
        return salesReturnRepo
                .findBySalesOrderIdAndCompanyIdOrderByCreatedAtDesc(salesOrderId, auth.getCurrentCompanyId())
                .stream()
                .map(this::toDTO)
                .toList();
    }

    public SalesReturnResponseDTO create(CreateSalesReturnDTO dto) {
        Long companyId = auth.getCurrentCompanyId();
        SalesOrder so = salesOrderRepo.findById(dto.getSalesOrderId())
                .filter(o -> o.getCompany() != null && companyId.equals(o.getCompany().getId()))
                .orElseThrow(() -> new NotFoundException("Sales order not found"));

        String status = so.getStatus() == null ? "" : so.getStatus().toUpperCase();
        if ("QUOTATION".equals(status) || "CANCELLED".equals(status)) {
            throw new ConflictException("Cannot return items on a quotation or cancelled order");
        }

        Map<Long, SalesOrderItem> linesById = new HashMap<>();
        Map<Long, List<SalesOrderItem>> linesByItemId = new HashMap<>();
        for (SalesOrderItem line : so.getItems()) {
            linesById.put(line.getId(), line);
            linesByItemId.computeIfAbsent(line.getItem().getId(), k -> new ArrayList<>()).add(line);
        }

        boolean restock = dto.getRestock() == null || dto.getRestock();
        BigDecimal totalReturn = BigDecimal.ZERO;
        List<PreparedLine> prepared = new ArrayList<>();

        for (CreateSalesReturnDTO.Line req : dto.getItems()) {
            if (req.getQuantity() == null || req.getQuantity() <= 0) {
                throw new ConflictException("Return quantity must be positive");
            }
            SalesOrderItem line = resolveLine(req, linesById, linesByItemId);
            int alreadyReturned = line.getReturnedQty() == null ? 0 : line.getReturnedQty();
            int ordered = line.getQuantity() == null ? 0 : line.getQuantity();
            int remaining = ordered - alreadyReturned;
            if (req.getQuantity() > remaining) {
                throw new ConflictException(
                        "Return qty " + req.getQuantity() + " exceeds remaining returnable qty ("
                                + remaining + ") for item " + line.getItem().getName());
            }

            BigDecimal unitPrice = line.getUnitPrice() != null ? line.getUnitPrice() : BigDecimal.ZERO;
            // Prefer proportional share of lineTotal when discounts/tax were applied
            BigDecimal lineValue = proportionalLineValue(line, req.getQuantity());
            totalReturn = totalReturn.add(lineValue);

            prepared.add(new PreparedLine(line, req.getQuantity(), unitPrice, lineValue));
        }

        if (prepared.isEmpty() || totalReturn.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ConflictException("Nothing to return");
        }

        User user = userRepo.findById(auth.getCurrentUserId()).orElse(null);
        Company company = so.getCompany();

        SalesReturn salesReturn = SalesReturn.builder()
                .returnNumber(documentSequenceService.generateNext("SR"))
                .salesOrder(so)
                .company(company)
                .totalAmount(totalReturn)
                .reason(dto.getReason())
                .restock(restock)
                .status("COMPLETED")
                .createdBy(user)
                .build();

        for (PreparedLine pl : prepared) {
            SalesOrderItem line = pl.line();
            int newReturned = (line.getReturnedQty() == null ? 0 : line.getReturnedQty()) + pl.qty();
            line.setReturnedQty(newReturned);

            // Reduce billed line totals / SO totals for the returned portion
            BigDecimal currentLineTotal = line.getLineTotal() != null ? line.getLineTotal() : BigDecimal.ZERO;
            line.setLineTotal(currentLineTotal.subtract(pl.lineValue()).max(BigDecimal.ZERO));
            if (line.getLineSubtotal() != null && orderedQty(line) > 0) {
                BigDecimal unitSub = line.getLineSubtotal()
                        .divide(BigDecimal.valueOf(orderedQty(line)), 4, RoundingMode.HALF_UP);
                line.setLineSubtotal(line.getLineSubtotal().subtract(unitSub.multiply(BigDecimal.valueOf(pl.qty())))
                        .max(BigDecimal.ZERO));
            }
            if (line.getTaxAmount() != null && orderedQty(line) > 0) {
                BigDecimal unitTax = line.getTaxAmount()
                        .divide(BigDecimal.valueOf(orderedQty(line)), 4, RoundingMode.HALF_UP);
                line.setTaxAmount(line.getTaxAmount().subtract(unitTax.multiply(BigDecimal.valueOf(pl.qty())))
                        .max(BigDecimal.ZERO));
            }

            Warehouse warehouse = line.getWarehouse() != null
                    ? line.getWarehouse()
                    : (line.getItem().getWarehouse());

            if (restock && warehouse != null) {
                stockBatchService.receiveIntoBatch(
                        line.getItem().getId(),
                        warehouse.getId(),
                        pl.qty(),
                        line.getFifoUnitCost() != null ? line.getFifoUnitCost() : line.getItem().getCostPrice(),
                        null,
                        null,
                        StockBatchSourceType.ADJUSTMENT,
                        null,
                        companyId
                );
            }

            SalesReturnItem sri = SalesReturnItem.builder()
                    .salesReturn(salesReturn)
                    .salesOrderItem(line)
                    .item(line.getItem())
                    .warehouse(warehouse)
                    .quantity(pl.qty())
                    .unitPrice(pl.unitPrice())
                    .lineTotal(pl.lineValue())
                    .build();
            salesReturn.getItems().add(sri);
        }

        recomputeSalesOrderTotals(so);
        salesOrderRepo.save(so);

        applyFinancialAdjustment(so, totalReturn, dto.getReason(), salesReturn);

        SalesReturn saved = salesReturnRepo.save(salesReturn);
        return toDTO(saved);
    }

    private void applyFinancialAdjustment(
            SalesOrder so, BigDecimal returnValue, String reason, SalesReturn salesReturn) {
        Invoice invoice = invoiceRepo.findByOrderIdAndType(so.getId(), InvoiceType.SALES).orElse(null);
        String noteReason = reason != null && !reason.isBlank()
                ? reason
                : "Customer return for sales order " + so.getOrderNumber();

        if (invoice == null) {
            // No invoice yet — SO totals already reduced above.
            return;
        }

        boolean settled = "PAID".equalsIgnoreCase(invoice.getStatus())
                || (invoice.getOutstanding() != null && invoice.getOutstanding().compareTo(BigDecimal.ZERO) <= 0);

        if (settled) {
            CreditNoteResponseDTO cn = creditNoteService.createAutomaticCreditNoteForCustomerReturn(
                    so.getCompany().getId(),
                    invoice.getInvoiceId(),
                    returnValue,
                    noteReason,
                    so.getCustomer() != null ? so.getCustomer().getId() : null
            );
            if (cn != null && cn.getId() != null) {
                creditNoteRepo.findById(cn.getId()).ifPresent(salesReturn::setCreditNote);
            }
        } else {
            invoiceService.reduceForReturnedSalesGoods(so.getId(), returnValue, noteReason);
        }
    }

    private static int orderedQty(SalesOrderItem line) {
        return line.getQuantity() == null ? 0 : line.getQuantity();
    }

    private static BigDecimal proportionalLineValue(SalesOrderItem line, int returnQty) {
        int ordered = orderedQty(line);
        if (ordered <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal base = line.getLineTotal() != null
                ? line.getLineTotal()
                : (line.getUnitPrice() != null
                        ? line.getUnitPrice().multiply(BigDecimal.valueOf(ordered))
                        : BigDecimal.ZERO);
        // Use original ordered qty for unit value so successive returns stay consistent
        // after lineTotal has been reduced. Prefer unitPrice * qty when line was already reduced.
        if (line.getUnitPrice() != null) {
            BigDecimal fromUnit = line.getUnitPrice().multiply(BigDecimal.valueOf(returnQty));
            // If discounts applied, scale original lineTotal by remaining+returned proportion is hard;
            // use current remaining value / remaining qty when available.
            int alreadyReturned = line.getReturnedQty() == null ? 0 : line.getReturnedQty();
            int remaining = ordered - alreadyReturned;
            if (remaining > 0 && line.getLineTotal() != null) {
                return line.getLineTotal()
                        .multiply(BigDecimal.valueOf(returnQty))
                        .divide(BigDecimal.valueOf(remaining), 2, RoundingMode.HALF_UP);
            }
            return fromUnit.setScale(2, RoundingMode.HALF_UP);
        }
        return base.multiply(BigDecimal.valueOf(returnQty))
                .divide(BigDecimal.valueOf(ordered), 2, RoundingMode.HALF_UP);
    }

    private void recomputeSalesOrderTotals(SalesOrder so) {
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        for (SalesOrderItem line : so.getItems()) {
            if (line.getLineSubtotal() != null) {
                subtotal = subtotal.add(line.getLineSubtotal());
            }
            if (line.getTaxAmount() != null) {
                tax = tax.add(line.getTaxAmount());
            }
            if (line.getLineTotal() != null) {
                total = total.add(line.getLineTotal());
            }
        }
        so.setSubtotalAmount(subtotal);
        so.setTaxAmount(tax);
        so.setTotalAmount(total);
    }

    private SalesOrderItem resolveLine(
            CreateSalesReturnDTO.Line req,
            Map<Long, SalesOrderItem> linesById,
            Map<Long, List<SalesOrderItem>> linesByItemId
    ) {
        if (req.getSalesOrderItemId() != null) {
            SalesOrderItem line = linesById.get(req.getSalesOrderItemId());
            if (line == null) {
                throw new NotFoundException("Sales order line not found: " + req.getSalesOrderItemId());
            }
            return line;
        }
        if (req.getItemId() == null) {
            throw new ConflictException("salesOrderItemId or itemId is required");
        }
        List<SalesOrderItem> matches = linesByItemId.getOrDefault(req.getItemId(), List.of());
        if (matches.isEmpty()) {
            throw new NotFoundException("Item not found on sales order: " + req.getItemId());
        }
        for (SalesOrderItem line : matches) {
            int already = line.getReturnedQty() == null ? 0 : line.getReturnedQty();
            int ordered = line.getQuantity() == null ? 0 : line.getQuantity();
            if (ordered - already > 0) {
                return line;
            }
        }
        return matches.get(0);
    }

    private SalesReturnResponseDTO toDTO(SalesReturn sr) {
        SalesOrder so = sr.getSalesOrder();
        CreditNote cn = sr.getCreditNote();
        return SalesReturnResponseDTO.builder()
                .id(sr.getId())
                .returnNumber(sr.getReturnNumber())
                .salesOrderId(so != null ? so.getId() : null)
                .salesOrderNumber(so != null ? so.getOrderNumber() : null)
                .customerId(so != null && so.getCustomer() != null ? so.getCustomer().getId() : null)
                .customerName(so != null && so.getCustomer() != null ? so.getCustomer().getCustomerName() : null)
                .totalAmount(sr.getTotalAmount())
                .reason(sr.getReason())
                .restock(sr.isRestock())
                .status(sr.getStatus())
                .creditNoteId(cn != null ? cn.getId() : null)
                .creditNoteNumber(cn != null ? cn.getCreditNoteNumber() : null)
                .creditNoteStatus(cn != null ? cn.getStatus() : null)
                .createdAt(sr.getCreatedAt())
                .items(sr.getItems() == null ? List.of() : sr.getItems().stream().map(i ->
                        SalesReturnResponseDTO.Line.builder()
                                .itemId(i.getItem() != null ? i.getItem().getId() : null)
                                .itemName(i.getItem() != null ? i.getItem().getName() : null)
                                .quantity(i.getQuantity())
                                .unitPrice(i.getUnitPrice())
                                .lineTotal(i.getLineTotal())
                                .build()
                ).toList())
                .build();
    }

    private record PreparedLine(SalesOrderItem line, int qty, BigDecimal unitPrice, BigDecimal lineValue) {}
}
