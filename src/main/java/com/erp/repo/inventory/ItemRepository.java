package com.erp.repo.inventory;

import com.erp.domain.inventory.Item;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ItemRepository extends JpaRepository<Item, Long> {

    List<Item> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    boolean existsBySkuAndCompanyId(String sku, Long companyId);
}
