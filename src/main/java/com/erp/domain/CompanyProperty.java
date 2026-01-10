package com.erp.domain;

import com.erp.domain.enums.PropertyStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "company_properties")
@Getter
@Setter
public class CompanyProperty {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    // ================= PROPERTY DETAILS =================

    @Column(name = "item_code", nullable = false, length = 50)
    private String itemCode;

    @Column(name = "item_name", nullable = false, length = 100)
    private String itemName;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_status", nullable = false, length = 20)
    private PropertyStatus itemStatus;

    @Column(name = "date_given", nullable = false)
    private LocalDate dateGiven;

    @Column(name = "return_date")
    private LocalDate returnDate;

    @Column(length = 500)
    private String description;
}
