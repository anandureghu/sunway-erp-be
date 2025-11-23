package com.erp.controller.finance;

import com.erp.dto.finance.CreatePaymentDTO;
import com.erp.dto.finance.PaymentResponseDTO;
import com.erp.service.finance.PaymentService;
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
    @PostMapping
    public PaymentResponseDTO createPayment(@RequestBody CreatePaymentDTO dto) {
        return paymentService.createPayment(dto);
    }

    // ----------------------------------------------------------
    // 2️⃣ Get a payment by ID
    // ----------------------------------------------------------
    @GetMapping("/{id}")
    public PaymentResponseDTO getPaymentById(@PathVariable("id") Long id) {
        return paymentService.getPaymentById(id);
    }

    // ----------------------------------------------------------
    // 3️⃣ List all payments for a company
    // ----------------------------------------------------------
    @GetMapping("/company/{companyId}")
    public List<PaymentResponseDTO> getPaymentsForCompany(@PathVariable("companyId") Long companyId) {
        return paymentService.getPaymentsForCompany(companyId);
    }

    // ----------------------------------------------------------
    // 4️⃣ List all payments made toward a specific invoice
    // ----------------------------------------------------------
    @GetMapping("/invoice/{invoiceId}")
    public List<PaymentResponseDTO> getPaymentsByInvoice(@PathVariable("invoiceId") String invoiceId) {
        return paymentService.getPaymentsByInvoice(invoiceId);
    }

    // ----------------------------------------------------------
    // 5️⃣ Delete a payment (rarely used in accounting)
    // ----------------------------------------------------------
    @DeleteMapping("/{id}")
    public void deletePayment(@PathVariable("id") Long id) {
        paymentService.deletePayment(id);
    }
}
