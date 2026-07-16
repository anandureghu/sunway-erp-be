package com.erp.controller.finance;

import com.erp.domain.finance.PaymentDirection;
import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.dto.finance.ConfirmPaymentDTO;
import com.erp.dto.finance.CreateOtherPaymentDTO;
import com.erp.dto.finance.CreatePaymentDTO;
import com.erp.dto.finance.PaymentResponseDTO;
import com.erp.service.finance.PaymentService;
import com.erp.service.security.annotation.RequiresPermission;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/finance/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    // ----------------------------------------------------------
    // 1️⃣ Create Payment (also auto-creates invoice + transaction)
    // ----------------------------------------------------------
    @RequiresPermission(module = AppModule.FINANCE_PAYMENT, action = {AppAction.CREATE})
    @PostMapping
    public PaymentResponseDTO createPayment(@RequestBody CreatePaymentDTO dto) {
        return paymentService.createPayment(dto);
    }

    // ----------------------------------------------------------
    // 1️⃣b Create an ad-hoc expense payment (rent, reimbursements, etc.)
    // ----------------------------------------------------------
    @RequiresPermission(module = AppModule.FINANCE_PAYMENT, action = {AppAction.CREATE})
    @PostMapping("/other")
    public PaymentResponseDTO createOtherPayment(@RequestBody CreateOtherPaymentDTO dto) {
        return paymentService.createOtherPayment(dto);
    }

    @RequiresPermission(module = AppModule.FINANCE_PAYMENT, action = {AppAction.EDIT})
    @PutMapping("/{id}")
    public PaymentResponseDTO updatePayment(@PathVariable("id") Long id, @RequestBody CreatePaymentDTO dto) {
        return paymentService.updatePayment(id, dto);
    }

    @RequiresPermission(module = AppModule.FINANCE_PAYMENT, action = {AppAction.APPROVE})
    @PostMapping("/{id}/confirm")
    public PaymentResponseDTO confirmPayment(
            @PathVariable("id") Long id,
            @RequestBody(required = false) ConfirmPaymentDTO body) {
        return paymentService.confirmPayment(id, body);
    }

    @RequiresPermission(module = AppModule.FINANCE_PAYMENT, action = {AppAction.DELETE})
    @PostMapping("/{id}/archive")
    public PaymentResponseDTO archivePayment(@PathVariable("id") Long id) {
        return paymentService.archivePayment(id);
    }

    // ----------------------------------------------------------
    // 2️⃣ Get a payment by ID
    // ----------------------------------------------------------
    @RequiresPermission(module = AppModule.FINANCE_PAYMENT, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping("/{id}")
    public PaymentResponseDTO getPaymentById(@PathVariable("id") Long id) {
        return paymentService.getPaymentById(id);
    }

    @RequiresPermission(module = AppModule.FINANCE_PAYMENT, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping("/{id}/pdf")
    public ResponseEntity<String> getVendorPaymentReceiptPdf(@PathVariable("id") Long id) {
        return ResponseEntity.ok(paymentService.getOrCreateVendorPaymentReceiptPdfUrl(id));
    }

    // ----------------------------------------------------------
    // 3️⃣ List all payments for a company
    // ----------------------------------------------------------
    @RequiresPermission(module = AppModule.FINANCE_PAYMENT, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping("/company/{companyId}")
    public List<PaymentResponseDTO> getPaymentsForCompany(
            @PathVariable("companyId") Long companyId,
            @RequestParam(value = "direction", required = false) String direction) {
        if (direction == null || direction.isBlank()) {
            return paymentService.getPaymentsForCompany(companyId);
        }
        return paymentService.getPaymentsForCompany(
                companyId, PaymentDirection.valueOf(direction.trim().toUpperCase()));
    }

    // ----------------------------------------------------------
    // 4️⃣ List all payments made toward a specific invoice
    // ----------------------------------------------------------
    @RequiresPermission(module = AppModule.FINANCE_PAYMENT, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping("/invoice/{invoiceId}")
    public List<PaymentResponseDTO> getPaymentsByInvoice(@PathVariable("invoiceId") String invoiceId) {
        return paymentService.getPaymentsByInvoice(invoiceId);
    }

    // ----------------------------------------------------------
    // 5️⃣ Delete a payment (rarely used in accounting)
    // ----------------------------------------------------------
    @RequiresPermission(module = AppModule.FINANCE_PAYMENT, action = {AppAction.DELETE})
    @DeleteMapping("/{id}")
    public void deletePayment(@PathVariable("id") Long id) {
        paymentService.deletePayment(id);
    }
}
