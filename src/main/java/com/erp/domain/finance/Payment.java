package com.erp.domain.finance;

import com.erp.domain.hr.Company;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Entity
@Table(name = "payments")
public class Payment {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_code", length = 64, unique = true)
    private String paymentCode;

    @ManyToOne
    @JoinColumn(name = "company_id")
    private Company company;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod; // e.g., BANK_TRANSFER, CASH

    @Column(name = "amount", precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "invoice_id", length = 64)
    private String invoiceId; // invoice business id / code

    @Column(name = "pdf_url", length = 500)
    private String pdfUrl;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "created_by")
    private Long createdBy;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
