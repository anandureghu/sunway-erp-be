package com.erp.domain.purchase;

import com.erp.domain.User;
import com.erp.domain.hr.Company;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "goods_receipts")
public class GoodsReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "purchase_order_id")
    private PurchaseOrder purchaseOrder;

    private Instant receivedAt;

    @ManyToOne
    @JoinColumn(name = "received_by")
    private User receivedBy;

    @ManyToOne
    @JoinColumn(name = "inspected_by")
    private User inspectedBy;

    @Column(name = "inspected_at")
    private Instant inspectedAt;

    @ManyToOne
    @JoinColumn(name = "authorized_by")
    private User authorizedBy;

    @ManyToOne(optional = false)
    @JoinColumn(name = "company_id")
    private Company company;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "goods_receipt_id")
    private List<GoodsReceiptItem> items;

    /** Public URL of generated goods receipt PDF (stored after receiving). */
    @Column(name = "document_pdf_url", length = 1024)
    private String documentPdfUrl;

    @PrePersist
    void onCreate() {
        receivedAt = Instant.now();
    }
}
