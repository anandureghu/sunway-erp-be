package com.erp.domain.finance;

import com.erp.domain.hr.Company;
import com.erp.domain.purchase.GoodsReceipt;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(
        name = "credit_notes",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"creditNoteNumber", "company_id"})
        }
)
public class CreditNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String creditNoteNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(nullable = false)
    private BigDecimal amount;

    private String reason;

    private LocalDate creditDate;

    private OffsetDateTime createdAt;

    private String status;

    @Column(nullable = false)
    private BigDecimal remainingAmount;

    private String project;

    /** "MANUAL" (default, user-entered) or "AUTO_REJECTION" (system-generated on inspection rejection). */
    @Column(nullable = false)
    @Builder.Default
    private String source = "MANUAL";

    /** The goods receipt/inspection event that generated this credit note, when auto-created. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goods_receipt_id")
    private GoodsReceipt goodsReceipt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;
}
