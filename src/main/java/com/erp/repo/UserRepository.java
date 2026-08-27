package com.erp.repo;

import com.erp.domain.User;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    /** Prefer {@link #findDistinctByEmployeeCompanyId} — users.company_id is stale under multi-membership. */
    List<User> findByCompany_IdOrderByCreatedAtDesc(Long companyId);

    /** Users with an Employee membership in the given company (true tenant membership). */
    @Query("""
            SELECT DISTINCT e.user FROM Employee e
            WHERE e.company.id = :companyId
              AND e.user IS NOT NULL
            ORDER BY e.user.fullName
            """)
    List<User> findDistinctByEmployeeCompanyId(@Param("companyId") Long companyId);

    @Query("""
            SELECT DISTINCT u FROM User u
            WHERE LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(u.username) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :q, '%'))
            ORDER BY u.fullName
            """)
    List<User> searchByKeyword(@Param("q") String q);
    Optional<User> findByEmail(String email);

    @Query("SELECT u FROM User u WHERE LOWER(u.email) = LOWER(:email)")
    Optional<User> findByEmailIgnoreCase(@Param("email") String email);
    Optional<User> findByUsername(String username);
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByCompanyRoleRef_Id(Long companyRoleId);

    /** Platform ADMIN users for a company (legacy users.company_id or employee membership). */
    @Query("""
            SELECT DISTINCT u FROM User u
            LEFT JOIN Employee e ON e.user = u
            WHERE u.role = com.erp.domain.security.Role.ADMIN
              AND (
                    u.company.id = :companyId
                    OR e.company.id = :companyId
                  )
            """)
    List<User> findAdminsForCompany(@Param("companyId") Long companyId);
}
