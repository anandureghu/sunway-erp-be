package com.erp.domain.inventory;

import com.erp.domain.User;
import com.erp.domain.hr.Company;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "items",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_items_company_sku", columnNames = {"company_id", "sku"})
        }
)
public class Item {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String sku;

    @Column(nullable = false, length = 150)
    private String name;

    private String type;
    private String category;

    @Column(name = "sub_category")
    private String subCategory;

    private String brand;
    private String location;

    private Integer quantity;
    private Integer available;
    private Integer reserved;

    private Integer minimum;
    private Integer maximum;

    private String barcode;

    @Column(name = "serial_no")
    private String serialNo;

    @Column(name = "date_received")
    private LocalDate dateReceived;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "unit_sale", precision = 18, scale = 2)
    private BigDecimal unitSale;

    @Column(name = "unit_measure")
    private String unitMeasure;

    @Column(name = "cost_price", precision = 18, scale = 2)
    private BigDecimal costPrice;

    @Column(name = "selling_price", precision = 18, scale = 2)
    private BigDecimal sellingPrice;

    /**
     * Undiscounted retail / list price. Bulk catalog discounts reduce
     * {@link #sellingPrice} from this baseline so discounts do not compound.
     */
    @Column(name = "list_price", precision = 18, scale = 2)
    private BigDecimal listPrice;

    @Column(name = "reorder_level")
    private Integer reorderLevel;

    private String status;

    @Builder.Default
    @Column(nullable = false)
    private boolean archived = false;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "archived_by")
    private User archivedBy;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Extra source columns from bulk import that do not map to first-class fields.
     * Stored as a JSON object of original header → value.
     */
    @Column(columnDefinition = "LONGTEXT")
    private String metadata;

    /* ===== AUDIT ===== */

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", updatable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    @ManyToOne(optional = false)
    @JoinColumn(name = "company_id")
    private Company company;

    @ManyToOne(optional = false)
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;
}
