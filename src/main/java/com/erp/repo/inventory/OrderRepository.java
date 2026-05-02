package com.erp.repo.inventory;

import com.erp.domain.inventory.Orders;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Orders, Long> {

    List<Orders> findBySupplier_Company_Id(Long companyId);
}
