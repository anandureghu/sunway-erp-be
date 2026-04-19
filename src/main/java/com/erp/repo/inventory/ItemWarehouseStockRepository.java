package com.erp.repo.inventory;

import com.erp.domain.inventory.ItemWarehouseStock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ItemWarehouseStockRepository extends JpaRepository<ItemWarehouseStock, Long> {

    Optional<ItemWarehouseStock> findByItemIdAndWarehouseId(Long itemId, Long warehouseId);

    List<ItemWarehouseStock> findByItemId(Long itemId);
}
