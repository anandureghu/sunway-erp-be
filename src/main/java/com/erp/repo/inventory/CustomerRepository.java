package com.erp.repo.inventory;

import com.erp.domain.inventory.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {
    List<Customer> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    @Query("""
            SELECT c FROM Customer c
            WHERE c.company.id = :companyId
              AND (
                :search IS NULL OR :search = ''
                OR LOWER(c.customerName) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(COALESCE(c.email, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(COALESCE(c.city, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(COALESCE(c.phoneNo, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(COALESCE(c.contactPersonName, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(COALESCE(c.country, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                OR CAST(c.id AS string) LIKE CONCAT('%', :search, '%')
              )
              AND (:active IS NULL OR c.isActive = :active)
              AND (
                :hasEmail IS NULL OR :hasEmail = false
                OR (c.email IS NOT NULL AND TRIM(c.email) <> '')
              )
              AND (
                :country IS NULL OR :country = '' OR :country = 'all'
                OR c.country = :country
              )
              AND (
                :city IS NULL OR :city = '' OR :city = 'all'
                OR c.city = :city
              )
              AND (
                :customerType IS NULL OR :customerType = '' OR :customerType = 'all'
                OR c.customerType = :customerType
              )
              AND (
                :phone IS NULL OR :phone = ''
                OR LOWER(COALESCE(c.phoneNo, '')) LIKE LOWER(CONCAT('%', :phone, '%'))
              )
              AND (
                :code IS NULL OR :code = ''
                OR CAST(c.id AS string) LIKE CONCAT('%', :code, '%')
              )
            """)
    Page<Customer> search(
            @Param("companyId") Long companyId,
            @Param("search") String search,
            @Param("active") Boolean active,
            @Param("hasEmail") Boolean hasEmail,
            @Param("country") String country,
            @Param("city") String city,
            @Param("customerType") String customerType,
            @Param("phone") String phone,
            @Param("code") String code,
            Pageable pageable
    );
}
