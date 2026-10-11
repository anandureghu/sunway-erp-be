package com.erp.service.notification;

import com.erp.domain.finance.Invoice;
import com.erp.domain.inventory.Customer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerEmailService {

    private final EmailService emailService;

    /** Best-effort (used by payment/order flows — never fails the transaction). */
    public void sendInvoiceCreatedEmail(Customer customer, Invoice invoice) {
        try {
            sendInvoiceCreatedEmailRequired(customer, invoice, null);
        } catch (Exception e) {
            log.warn("Skipping invoice email: {}", e.getMessage());
        }
    }

    /** Best-effort (used by payment flows — never fails the transaction). */
    public void sendReceiptEmail(Customer customer, Invoice invoice) {
        try {
            sendReceiptEmailRequired(customer, invoice, null);
        } catch (Exception e) {
            log.warn("Skipping receipt email: {}", e.getMessage());
        }
    }

    public void sendInvoiceCreatedEmailRequired(Customer customer, Invoice invoice) {
        sendInvoiceCreatedEmailRequired(customer, invoice, null);
    }

    public void sendInvoiceCreatedEmailRequired(Customer customer, Invoice invoice, byte[] pdfBytes) {
        if (customer == null || customer.getEmail() == null || customer.getEmail().isBlank()) {
            throw new IllegalStateException("Customer email is missing on this invoice");
        }
        String subject = "Invoice " + invoice.getInvoiceId() + " - Payment requested";
        String body = "Dear Customer,\n\n"
                + "A new invoice has been generated for your sales order.\n"
                + "Invoice Number: " + invoice.getInvoiceId() + "\n"
                + "Amount: " + invoice.getAmount() + "\n"
                + "Due Date: " + invoice.getDueDate() + "\n\n"
                + "Please complete payment to proceed with order processing.\n\n"
                + "Regards,\nSunway ERP";
        sendMailRequired(
                customer.getEmail(),
                subject,
                body,
                pdfBytes,
                invoice.getInvoiceId() + ".pdf");
    }

    public void sendReceiptEmailRequired(Customer customer, Invoice invoice) {
        sendReceiptEmailRequired(customer, invoice, null);
    }

    public void sendReceiptEmailRequired(Customer customer, Invoice invoice, byte[] pdfBytes) {
        if (customer == null || customer.getEmail() == null || customer.getEmail().isBlank()) {
            throw new IllegalStateException("Customer email is missing on this invoice");
        }
        String subject = "Receipt - " + invoice.getInvoiceId();
        String body = "Dear Customer,\n\n"
                + "Payment has been received for invoice " + invoice.getInvoiceId() + ".\n"
                + "Paid Date: " + invoice.getPaidDate() + "\n"
                + "Amount: " + invoice.getAmount() + "\n\n"
                + "Thank you for your payment.\n\n"
                + "Regards,\nSunway ERP";
        sendMailRequired(
                customer.getEmail(),
                subject,
                body,
                pdfBytes,
                invoice.getInvoiceId() + "-receipt.pdf");
    }

    public void sendPurchaseInvoiceEmailRequired(String supplierName, String supplierEmail, Invoice invoice) {
        sendPurchaseInvoiceEmailRequired(supplierName, supplierEmail, invoice, null);
    }

    public void sendPurchaseInvoiceEmailRequired(
            String supplierName, String supplierEmail, Invoice invoice, byte[] pdfBytes) {
        if (supplierEmail == null || supplierEmail.isBlank()) {
            throw new IllegalStateException("Supplier email is missing on this purchase order");
        }
        String name = supplierName == null || supplierName.isBlank() ? "Supplier" : supplierName;
        String subject = "Purchase invoice " + invoice.getInvoiceId();
        String body = "Dear " + name + ",\n\n"
                + "Please find details for purchase invoice " + invoice.getInvoiceId() + ".\n"
                + "Amount: " + invoice.getAmount() + "\n"
                + "Due Date: " + invoice.getDueDate() + "\n\n"
                + "Regards,\nSunway ERP";
        sendMailRequired(
                supplierEmail,
                subject,
                body,
                pdfBytes,
                invoice.getInvoiceId() + ".pdf");
    }

    public void sendPurchaseReceiptEmailRequired(String supplierName, String supplierEmail, Invoice invoice) {
        sendPurchaseReceiptEmailRequired(supplierName, supplierEmail, invoice, null);
    }

    public void sendPurchaseReceiptEmailRequired(
            String supplierName, String supplierEmail, Invoice invoice, byte[] pdfBytes) {
        if (supplierEmail == null || supplierEmail.isBlank()) {
            throw new IllegalStateException("Supplier email is missing on this purchase order");
        }
        String name = supplierName == null || supplierName.isBlank() ? "Supplier" : supplierName;
        String subject = "Payment receipt - " + invoice.getInvoiceId();
        String body = "Dear " + name + ",\n\n"
                + "Payment has been recorded for purchase invoice " + invoice.getInvoiceId() + ".\n"
                + "Paid Date: " + invoice.getPaidDate() + "\n"
                + "Amount: " + invoice.getAmount() + "\n\n"
                + "Regards,\nSunway ERP";
        sendMailRequired(
                supplierEmail,
                subject,
                body,
                pdfBytes,
                invoice.getInvoiceId() + "-receipt.pdf");
    }

    private void sendMailRequired(
            String to, String subject, String text, byte[] pdfBytes, String filename) {
        try {
            if (pdfBytes != null && pdfBytes.length > 0) {
                emailService.sendWithPdfAttachmentRequired(to, subject, text, pdfBytes, filename);
            } else {
                emailService.sendPlainTextRequired(to, subject, text);
            }
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to send email: " + e.getMessage(), e);
        }
    }
}
