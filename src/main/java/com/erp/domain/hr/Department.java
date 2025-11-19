package com.erp.domain.hr;

import com.erp.domain.Employee;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "departments")
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "department_code", unique = true, nullable = false)
    private String departmentCode;

    @Column(name = "department_name", nullable = false)
    private String departmentName;

    // 🔥 Manager now mapped to Employee
    @ManyToOne
    @JoinColumn(name = "manager_id", referencedColumnName = "id")
    private Employee manager;

    @ManyToOne
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    private Instant createdAt;

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
