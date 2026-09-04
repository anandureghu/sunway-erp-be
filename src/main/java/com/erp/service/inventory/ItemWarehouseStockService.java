package com.erp.service.inventory;

import com.erp.domain.inventory.Item;
import com.erp.domain.inventory.ItemWarehouseStock;
import com.erp.domain.inventory.Warehouse;
import com.erp.dto.inventory.ItemWarehouseStockRowDTO;
import com.erp.repo.inventory.ItemRepository;
import com.erp.repo.inventory.ItemWarehouseStockRepository;
import com.erp.repo.inventory.WarehouseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class ItemWarehouseStockService {

    private final ItemWarehouseStockRepository stockRepo;
    private final ItemRepository itemRepo;
    private final WarehouseRepository warehouseRepo;

    public ItemWarehouseStockService(
            ItemWarehouseStockRepository stockRepo,
            ItemRepository itemRepo,
            WarehouseRepository warehouseRepo
    ) {
        this.stockRepo = stockRepo;
        this.itemRepo = itemRepo;
        this.warehouseRepo = warehouseRepo;
    }

    public void ensureInitialRowForNewItem(Item item) {
        if (item == null || item.getWarehouse() == null) {
            return;
        }
        Warehouse wh = item.getWarehouse();
        stockRepo.findByItemIdAndWarehouseId(item.getId(), wh.getId())
                .orElseGet(() -> stockRepo.save(ItemWarehouseStock.builder()
                        .item(item)
                        .warehouse(wh)
                        .quantityOnHand(nz(item.getQuantity()))
                        .reserved(nz(item.getReserved()))
                        .build()));
    }

    /**
     * Seed rows from legacy Item aggregates (one row per item's default warehouse).
     */
    public void seedMissingRowsFromItems() {
        List<Item> items = itemRepo.findAll();
        for (Item item : items) {
            if (item.getWarehouse() == null) {
                continue;
            }
            Long wid = item.getWarehouse().getId();
            if (stockRepo.findByItemIdAndWarehouseId(item.getId(), wid).isEmpty()) {
                stockRepo.save(ItemWarehouseStock.builder()
                        .item(item)
                        .warehouse(item.getWarehouse())
                        .quantityOnHand(nz(item.getQuantity()))
                        .reserved(nz(item.getReserved()))
                        .build());
            }
        }
    }

    /**
     * Moves inventory change through the item's default warehouse row when editing total quantity on the item.
     */
    public void applyDeltaToDefaultWarehouse(Item item, int delta) {
        if (delta == 0) {
            return;
        }
        Warehouse def = item.getWarehouse();
        if (def == null) {
            return;
        }
        ItemWarehouseStock row = getOrCreateStockRow(item, def);
        int newOn = nz(row.getQuantityOnHand()) + delta;
        if (newOn < nz(row.getReserved())) {
            throw new IllegalArgumentException("Adjustment would leave less on hand than reserved at default warehouse");
        }
        row.setQuantityOnHand(newOn);
        stockRepo.save(row);
        syncItemAggregates(item);
    }

    public void syncItemAggregates(Item item) {
        List<ItemWarehouseStock> rows = stockRepo.findByItemId(item.getId());
        int q = 0;
        int res = 0;
        for (ItemWarehouseStock row : rows) {
            q += nz(row.getQuantityOnHand());
            res += nz(row.getReserved());
        }
        item.setQuantity(q);
        item.setReserved(res);
        item.setAvailable(Math.max(0, q - res));
        itemRepo.save(item);
    }

    public ItemWarehouseStock getOrCreateStockRow(Item item, Warehouse warehouse) {
        validateSameCompany(item, warehouse);
        return stockRepo.findByItemIdAndWarehouseId(item.getId(), warehouse.getId())
                .orElseGet(() -> stockRepo.save(ItemWarehouseStock.builder()
                        .item(item)
                        .warehouse(warehouse)
                        .quantityOnHand(0)
                        .reserved(0)
                        .build()));
    }

    public void addIncomingStock(Long itemId, Long warehouseId, int quantity, Long companyId) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Incoming quantity must be positive");
        }
        Item item = loadItemForCompany(itemId, companyId);
        Warehouse wh = loadWarehouseForCompany(warehouseId, companyId);
        ItemWarehouseStock row = getOrCreateStockRow(item, wh);
        row.setQuantityOnHand(nz(row.getQuantityOnHand()) + quantity);
        stockRepo.save(row);
        syncItemAggregates(item);
    }

    public void adjustRowToAbsoluteQuantity(
            Long itemId,
            Long warehouseId,
            int newQuantityOnHand,
            Long companyId
    ) {
        Item item = loadItemForCompany(itemId, companyId);
        Warehouse wh = loadWarehouseForCompany(warehouseId, companyId);
        ItemWarehouseStock row = getOrCreateStockRow(item, wh);
        if (newQuantityOnHand < nz(row.getReserved())) {
            throw new IllegalArgumentException(
                    "New quantity cannot be less than reserved quantity at this warehouse");
        }
        row.setQuantityOnHand(newQuantityOnHand);
        stockRepo.save(row);
        syncItemAggregates(item);
    }

    public void assertAvailableForSale(Long itemId, Long warehouseId, int quantity, Long companyId) {
        if (quantity <= 0) {
            return;
        }
        Item item = loadItemForCompany(itemId, companyId);
        if (item.isNegativeStockPermitted()) {
            return;
        }
        Warehouse wh = loadWarehouseForCompany(warehouseId, companyId);
        ItemWarehouseStock row = stockRepo.findByItemIdAndWarehouseId(itemId, warehouseId)
                .orElseThrow(() -> new RuntimeException(
                        "No stock record for this item at the selected warehouse. Receive stock first."));
        int av = row.available();
        if (av < quantity) {
            throw new RuntimeException(
                    "Insufficient stock in warehouse "
                            + wh.getName()
                            + ". Available: "
                            + av
                            + ", requested: "
                            + quantity);
        }
    }

    public void decreaseForConfirmedSale(Long itemId, Long warehouseId, int quantity, Long companyId) {
        if (quantity <= 0) {
            return;
        }
        Item item = loadItemForCompany(itemId, companyId);
        Warehouse wh = loadWarehouseForCompany(warehouseId, companyId);
        ItemWarehouseStock row = stockRepo.findByItemIdAndWarehouseId(itemId, warehouseId)
                .orElseThrow(() -> new RuntimeException("No stock for item at warehouse"));
        int av = row.available();
        if (!item.isNegativeStockPermitted() && av < quantity) {
            throw new RuntimeException(
                    "Insufficient stock at warehouse " + wh.getName() + " available " + av + " for sale " + quantity);
        }
        row.setQuantityOnHand(nz(row.getQuantityOnHand()) - quantity);
        stockRepo.save(row);
        syncItemAggregates(item);
    }

    /**
     * Hold stock for a confirmed sales order: increases reserved without reducing on-hand.
     */
    public void reserveForSale(Long itemId, Long warehouseId, int quantity, Long companyId) {
        if (quantity <= 0) {
            return;
        }
        assertAvailableForSale(itemId, warehouseId, quantity, companyId);
        Item item = loadItemForCompany(itemId, companyId);
        Warehouse wh = loadWarehouseForCompany(warehouseId, companyId);
        ItemWarehouseStock row = getOrCreateStockRow(item, wh);
        row.setReserved(nz(row.getReserved()) + quantity);
        stockRepo.save(row);
        syncItemAggregates(item);
    }

    /**
     * Release a prior sales reservation (e.g. cancel confirmed SO before dispatch).
     */
    public void releaseReservation(Long itemId, Long warehouseId, int quantity, Long companyId) {
        if (quantity <= 0) {
            return;
        }
        Item item = loadItemForCompany(itemId, companyId);
        Warehouse wh = loadWarehouseForCompany(warehouseId, companyId);
        ItemWarehouseStock row = stockRepo.findByItemIdAndWarehouseId(itemId, warehouseId)
                .orElseThrow(() -> new RuntimeException(
                        "No stock for item at warehouse " + wh.getName()));
        int reserved = nz(row.getReserved());
        if (reserved < quantity) {
            throw new RuntimeException(
                    "Cannot release reservation at warehouse " + wh.getName()
                            + ": reserved " + reserved + ", requested " + quantity);
        }
        row.setReserved(reserved - quantity);
        stockRepo.save(row);
        syncItemAggregates(item);
    }

    /**
     * Dispatch fulfillment: reduce on-hand and clear the matching reservation atomically.
     */
    public void decreaseForDispatchedSale(Long itemId, Long warehouseId, int quantity, Long companyId) {
        if (quantity <= 0) {
            return;
        }
        Item item = loadItemForCompany(itemId, companyId);
        Warehouse wh = loadWarehouseForCompany(warehouseId, companyId);
        ItemWarehouseStock row = stockRepo.findByItemIdAndWarehouseId(itemId, warehouseId)
                .orElseThrow(() -> new RuntimeException("No stock for item at warehouse"));
        int onHand = nz(row.getQuantityOnHand());
        int reserved = nz(row.getReserved());
        if (!item.isNegativeStockPermitted() && onHand < quantity) {
            throw new RuntimeException(
                    "Insufficient on-hand at warehouse " + wh.getName()
                            + " on-hand " + onHand + " for dispatch " + quantity);
        }
        if (reserved < quantity) {
            throw new RuntimeException(
                    "Insufficient reserved stock at warehouse " + wh.getName()
                            + " reserved " + reserved + " for dispatch " + quantity);
        }
        row.setQuantityOnHand(onHand - quantity);
        row.setReserved(reserved - quantity);
        stockRepo.save(row);
        syncItemAggregates(item);
    }

    public void restoreForCancelledSale(Long itemId, Long warehouseId, int quantity, Long companyId) {
        if (quantity <= 0) {
            return;
        }
        Item item = loadItemForCompany(itemId, companyId);
        Warehouse wh = loadWarehouseForCompany(warehouseId, companyId);
        ItemWarehouseStock row = getOrCreateStockRow(item, wh);
        row.setQuantityOnHand(nz(row.getQuantityOnHand()) + quantity);
        stockRepo.save(row);
        syncItemAggregates(item);
    }

    public void transferBetweenWarehouses(
            Long itemId,
            Long fromWarehouseId,
            Long toWarehouseId,
            int quantity,
            Long companyId
    ) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Transfer quantity must be positive");
        }
        if (fromWarehouseId.equals(toWarehouseId)) {
            throw new IllegalArgumentException("Source and destination warehouse must differ");
        }
        Item item = loadItemForCompany(itemId, companyId);
        Warehouse fromWh = loadWarehouseForCompany(fromWarehouseId, companyId);
        Warehouse toWh = loadWarehouseForCompany(toWarehouseId, companyId);

        ItemWarehouseStock fromRow = stockRepo.findByItemIdAndWarehouseId(itemId, fromWarehouseId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No stock for this item at the source warehouse"));
        if (fromRow.available() < quantity) {
            throw new IllegalArgumentException(
                    "Insufficient available stock at "
                            + fromWh.getName()
                            + ". Available: "
                            + fromRow.available()
                            + ", requested: "
                            + quantity);
        }

        fromRow.setQuantityOnHand(nz(fromRow.getQuantityOnHand()) - quantity);
        stockRepo.save(fromRow);

        ItemWarehouseStock toRow = getOrCreateStockRow(item, toWh);
        toRow.setQuantityOnHand(nz(toRow.getQuantityOnHand()) + quantity);
        stockRepo.save(toRow);

        syncItemAggregates(item);
    }

    public int getQuantityOnHand(Long itemId, Long warehouseId, Long companyId) {
        Item item = loadItemForCompany(itemId, companyId);
        loadWarehouseForCompany(warehouseId, companyId);
        return stockRepo.findByItemIdAndWarehouseId(item.getId(), warehouseId)
                .map(row -> nz(row.getQuantityOnHand()))
                .orElse(0);
    }

    public void applyDelta(Long itemId, Long warehouseId, int delta, Long companyId) {
        if (delta == 0) {
            return;
        }
        Item item = loadItemForCompany(itemId, companyId);
        Warehouse wh = loadWarehouseForCompany(warehouseId, companyId);
        ItemWarehouseStock row = getOrCreateStockRow(item, wh);
        int newQty = nz(row.getQuantityOnHand()) + delta;
        if (newQty < nz(row.getReserved())) {
            throw new IllegalArgumentException(
                    "Adjustment would leave less on hand than reserved at this warehouse");
        }
        if (newQty < 0) {
            throw new IllegalArgumentException("Resulting quantity cannot be negative");
        }
        row.setQuantityOnHand(newQty);
        stockRepo.save(row);
        syncItemAggregates(item);
    }

    @Transactional(readOnly = true)
    public List<ItemWarehouseStockRowDTO> listStockForItem(Long itemId, Long companyId) {
        Item item = loadItemForCompany(itemId, companyId);
        return stockRepo.findByItemId(item.getId()).stream()
                .map(row -> ItemWarehouseStockRowDTO.builder()
                        .warehouseId(row.getWarehouse().getId())
                        .warehouseName(row.getWarehouse().getName())
                        .quantityOnHand(nz(row.getQuantityOnHand()))
                        .reserved(nz(row.getReserved()))
                        .available(row.available())
                        .build())
                .toList();
    }

    public record StockCatalogRow(
            Item item,
            Warehouse warehouse,
            int quantityOnHand,
            int reserved,
            int available
    ) {}

    @Transactional(readOnly = true)
    public List<StockCatalogRow> listStockCatalog(Long companyId, boolean archived) {
        return stockRepo.findAllByCompanyIdAndArchived(companyId, archived).stream()
                .map(row -> new StockCatalogRow(
                        row.getItem(),
                        row.getWarehouse(),
                        nz(row.getQuantityOnHand()),
                        nz(row.getReserved()),
                        row.available()))
                .toList();
    }

    private Item loadItemForCompany(Long itemId, Long companyId) {
        Item item = itemRepo.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Item not found"));
        if (!item.getCompany().getId().equals(companyId)) {
            throw new RuntimeException("Item not found");
        }
        return item;
    }

    private Warehouse loadWarehouseForCompany(Long warehouseId, Long companyId) {
        Warehouse wh = warehouseRepo.findById(warehouseId)
                .orElseThrow(() -> new RuntimeException("Warehouse not found"));
        if (!wh.getCompany().getId().equals(companyId)) {
            throw new RuntimeException("Warehouse not found");
        }
        return wh;
    }

    private static void validateSameCompany(Item item, Warehouse warehouse) {
        if (!item.getCompany().getId().equals(warehouse.getCompany().getId())) {
            throw new RuntimeException("Item and warehouse must belong to the same company");
        }
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }
}
