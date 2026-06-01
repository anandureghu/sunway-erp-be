package com.erp.domain.purchase;

import com.erp.domain.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "purchase_requisition_documents")
public class PurchaseRequisitionDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "requisition_id")
    private PurchaseRequisition requisition;

    @Column(nullable = false, length = 512)
    private String fileName;

    @Column(nullable = false, length = 1024)
    private String blobPath;

    private String contentType;
    private Long fileSizeBytes;

    @ManyToOne
    @JoinColumn(name = "uploaded_by")
    private User uploadedBy;

    private Instant uploadedAt;

    @PrePersist
    void onCreate() {
        uploadedAt = Instant.now();
    }
}
