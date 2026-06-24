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
    private AppModule module;

    private boolean viewOwn;
    private boolean viewAll;
    private boolean createOwn;
    private boolean createAll;
    private boolean editOwn;
    private boolean editAll;
    private boolean deleteOwn;
    private boolean deleteAll;
    private boolean approve;

    /**
     * When false the rule is saved but not enforced — the resolver skips it and
     * falls through to the next precedence layer. Defaults to true.
     */
    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;
}
