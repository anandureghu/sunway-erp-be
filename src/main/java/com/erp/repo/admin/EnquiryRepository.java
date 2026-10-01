package com.erp.repo.admin;

import com.erp.domain.admin.Enquiry;
import com.erp.domain.admin.EnquiryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EnquiryRepository extends JpaRepository<Enquiry, Long> {

    Optional<Enquiry> findByEmailIgnoreCase(String email);

    @Query("""
            SELECT e FROM Enquiry e
            WHERE (:status IS NULL OR e.status = :status)
              AND (
                :search IS NULL OR :search = ''
                OR LOWER(e.name) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(e.email) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(COALESCE(e.phone, '')) LIKE LOWER(CONCAT('%', :search, '%'))
              )
            """)
    Page<Enquiry> search(
            @Param("status") EnquiryStatus status,
            @Param("search") String search,
            Pageable pageable
    );
}
