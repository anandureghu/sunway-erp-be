package com.erp.repo;

import com.erp.domain.User;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    List<User> findByCompany_IdOrderByCreatedAtDesc(Long companyId);

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
}
