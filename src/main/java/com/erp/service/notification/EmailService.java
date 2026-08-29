package com.erp.service.notification;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${app.mail.from:no-reply@sunwayerp.local}")
    private String fromAddress;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    public void sendPlainText(String to, String subject, String body) {
        if (to == null || to.isBlank()) {
            throw new IllegalArgumentException("Recipient email is required");
        }

        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (!mailEnabled || mailSender == null) {
            log.warn("Mail is not configured. Skipping email to {} with subject '{}'", to, subject);
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(to.trim());
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
        log.info("Email sent to {} with subject '{}'", maskEmail(to), subject);
    }

    public void sendWithPdfAttachment(
            String to,
            String subject,
            String body,
            byte[] pdfBytes,
            String filename
    ) {
        sendWithPdfAttachment(
                to == null || to.isBlank() ? List.of() : List.of(to.trim()),
                subject,
                body,
                pdfBytes,
                filename
        );
    }

    public void sendWithPdfAttachment(
            List<String> recipients,
            String subject,
            String body,
            byte[] pdfBytes,
            String filename
    ) {
        List<String> tos = normalizeRecipients(recipients);
        if (tos.isEmpty()) {
            throw new IllegalArgumentException("At least one recipient email is required");
        }
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new IllegalArgumentException("PDF attachment is required");
        }

        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (!mailEnabled || mailSender == null) {
            log.warn(
                    "Mail is not configured. Skipping email with attachment to {} subject '{}'",
                    tos,
                    subject
            );
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(tos.toArray(String[]::new));
            helper.setSubject(subject);
            helper.setText(body, false);
            helper.addAttachment(
                    filename != null && !filename.isBlank() ? filename : "invoice.pdf",
                    new ByteArrayResource(pdfBytes)
            );
            mailSender.send(message);
            log.info(
                    "Email with PDF sent to {} subject '{}'",
                    tos.stream().map(EmailService::maskEmail).toList(),
                    subject
            );
        } catch (Exception e) {
            String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            if (detail.toLowerCase().contains("authentication failed")) {
                detail += ". Verify MAIL_USERNAME, MAIL_PASSWORD, and MAIL_FROM on the server "
                        + "(use an app password for Gmail/Microsoft SMTP).";
            }
            throw new RuntimeException("Failed to send email with attachment: " + detail, e);
        }
    }

    private static List<String> normalizeRecipients(List<String> recipients) {
        if (recipients == null || recipients.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String raw : recipients) {
            if (raw == null) continue;
            String email = raw.trim();
            if (email.isEmpty()) continue;
            unique.add(email);
        }
        return List.copyOf(unique);
    }

    public boolean isConfigured() {
        return mailEnabled && mailSenderProvider.getIfAvailable() != null;
    }

    private static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
