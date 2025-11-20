package com.erp.repo.hr;

import com.erp.domain.hr.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CompanyRepository extends JpaRepository<Company, Long> {
    // Fetch all companies created by a given user
    List<Company> findByCreatedBy(String createdBy);
}
