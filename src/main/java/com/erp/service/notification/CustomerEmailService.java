package com.erp.service.notification;

import com.erp.domain.finance.Invoice;
import com.erp.domain.inventory.Customer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerEmailService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${app.mail.from:no-reply@sunwayerp.local}")
    private String fromAddress;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    public void sendInvoiceCreatedEmail(Customer customer, Invoice invoice) {
        if (customer == null || customer.getEmail() == null || customer.getEmail().isBlank()) {
            return;
        }
        String subject = "Invoice " + invoice.getInvoiceId() + " - Payment requested";
        String body = "Dear Customer,\n\n"
                + "A new invoice has been generated for your sales order.\n"
                + "Invoice Number: " + invoice.getInvoiceId() + "\n"
                + "Amount: " + invoice.getAmount() + "\n"
                + "Due Date: " + invoice.getDueDate() + "\n\n"
                + "Please complete payment to proceed with order processing.\n\n"
                + "Regards,\nSunway ERP";
        sendMail(customer.getEmail(), subject, body);
    }

    public void sendReceiptEmail(Customer customer, Invoice invoice) {
        if (customer == null || customer.getEmail() == null || customer.getEmail().isBlank()) {
            return;
        }
        String subject = "Receipt - " + invoice.getInvoiceId();
        String body = "Dear Customer,\n\n"
                + "Payment has been received for invoice " + invoice.getInvoiceId() + ".\n"
                + "Paid Date: " + invoice.getPaidDate() + "\n"
                + "Amount: " + invoice.getAmount() + "\n\n"
                + "Thank you for your payment.\n\n"
                + "Regards,\nSunway ERP";
        sendMail(customer.getEmail(), subject, body);
    }

    private void sendMail(String to, String subject, String text) {
        if (!mailEnabled) {
            log.debug("Mail disabled. Skipping email to {} with subject '{}'", to, subject);
            return;
        }
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            log.info("Email provider not configured. Skipping email to {} with subject '{}'", to, subject);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
        } catch (Exception e) {
            // Never fail the business transaction (e.g. payment confirm) because SMTP auth failed.
            log.warn("Failed to send email to {} subject '{}': {}", to, subject, e.getMessage());
        }
    }
}
