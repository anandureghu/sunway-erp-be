package com.erp.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "company_numbering_configs",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_company_doc_type",
        columnNames = {"company_id", "doc_type"}
    )
)
@Getter
@Setter
@NoArgsConstructor
public class CompanyNumberingConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /** Internal key that matches the prefix string passed to DocumentSequenceService (e.g. EMP, PO, PR). */
    @Column(name = "doc_type", nullable = false, length = 30)
    private String docType;

    /** Display prefix used in the generated number (e.g. "EMP", "PO", "ORD"). Empty = no prefix. */
    @Column(name = "prefix", length = 20)
    private String prefix;

    /** Starting value when the sequence is first seeded. Default 1000. */
    @Column(name = "start_number", nullable = false)
    private Long startNumber = 1000L;

    public CompanyNumberingConfig(Long companyId, String docType, String prefix, Long startNumber) {
        this.companyId = companyId;
        this.docType = docType;
        this.prefix = prefix;
        this.startNumber = startNumber != null ? startNumber : 1000L;
    }
}
