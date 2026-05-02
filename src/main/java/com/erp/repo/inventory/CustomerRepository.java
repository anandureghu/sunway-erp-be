package com.erp.repo.inventory;

import com.erp.domain.inventory.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {
    List<Customer> findByCompanyIdOrderByCreatedAtDesc(Long companyId);
}
