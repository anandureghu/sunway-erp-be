package com.erp.domain;

import com.erp.domain.hr.Company;
import com.erp.domain.hr.Department;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "employees")
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_no", unique = true, nullable = false)
    private String employeeNo;

    @Column(name = "first_name", length = 50, nullable = false)
    private String firstName;

    @Column(name = "last_name", length = 50, nullable = false)
    private String lastName;

    @Column(length = 20)
    private String prefix;

    @Column(length = 20)
    private String gender;

    /**
     * ACTIVE | INACTIVE | ON_LEAVE
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private EmployeeStatus status;

    private LocalDate dateOfBirth;

    @Column(length = 30)
    private String maritalStatus;

    private LocalDate joinDate;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    @OneToOne(
            mappedBy = "employee",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    private EmployeeContactInfo contactInfo;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        if (this.status == null) {
            this.status = EmployeeStatus.ACTIVE;
        }
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // helpers
    public String getPhoneNo() {
        return contactInfo != null ? contactInfo.getPhone() : null;
    }

    public String getAltPhone() {
        return contactInfo != null ? contactInfo.getAltPhone() : null;
    }

    public String getEmail() {
        return user != null ? user.getEmail() : null;
    }
}
