package com.erp.domain.security;

import com.erp.domain.Employee;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "employee_permissions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_employee_permission_module",
                columnNames = {"employee_id", "module"}
        )
)
public class EmployeePermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HrModule module;

    private boolean viewOwn;
    private boolean viewAll;
    private boolean createPermission;
    private boolean editPermission;
    private boolean deletePermission;
    private boolean approve;
}
