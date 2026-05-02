package com.erp.domain.security;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "enum_role_permissions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_enum_role_module",
                columnNames = {"role", "module"}
        )
)
public class EnumRolePermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Role role;

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
