package com.erp.domain;

import com.erp.domain.hr.Company;
import com.erp.domain.hr.Department;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

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

    @Column(name = "employee_no", unique = true)
    private Long employeeNo;

    @Column(name = "first_name", length = 50)
    private String firstName;

    @Column(name = "last_name", length = 50)
    private String lastName;

    @Column(name = "phone_no", length = 50)
    private String phoneNo;

    // 🔗 Employee belongs to a Company
    @ManyToOne
    @JoinColumn(name = "company_id")
    private Company company;

    // 🔗 Employee belongs to a Department
    @ManyToOne
    @JoinColumn(name = "department_id")
    private Department department;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;


    @PreUpdate
    public void onUpdate() {
        updatedAt = Instant.now();
    }
}
