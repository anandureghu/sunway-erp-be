package com.erp.domain.security;

import com.erp.domain.hr.CompanyRole;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "company_role_permissions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_company_role_module",
                columnNames = {"company_role_id", "module"}
        )
)
public class CompanyRolePermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_role_id", nullable = false)
    private CompanyRole companyRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppModule module;

    private boolean viewOwn;
    private boolean viewAll;
    private boolean createPermission;
    private boolean editPermission;
    private boolean deletePermission;
    private boolean approve;
}
