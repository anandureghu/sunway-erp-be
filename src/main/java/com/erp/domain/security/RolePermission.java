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
@Table(name = "role_permissions",
        uniqueConstraints = {
                // One permission rule per role+module combination
                @UniqueConstraint(name = "uk_role_module",          columnNames = {"role", "module"}),
                // One permission rule per employee+module combination
                @UniqueConstraint(name = "uk_employee_module",      columnNames = {"employee_id", "module"})
        })
public class RolePermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Stores the role name as a plain String.
     * For company roles: "HR Manager", "External Auditor", "Finance Lead" etc.
     * For security roles fallback: "ADMIN", "HR", "USER" etc.
     * Never use Role enum here — company roles have spaces and don't map to enum values.
     */
    @Column(nullable = false, length = 100)
    private String role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppModule module;

    /**
     * If set — this rule applies to this specific employee only (overrides role-level rule).
     * If null — this rule applies to everyone with the matching role.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    private boolean viewOwn;
    private boolean viewAll;
    private boolean createPermission;
    private boolean editPermission;
    private boolean deletePermission;
    private boolean approve;
}