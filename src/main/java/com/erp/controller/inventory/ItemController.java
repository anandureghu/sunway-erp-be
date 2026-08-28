package com.erp.controller.inventory;

import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.dto.inventory.ItemBulkDiscountRequestDTO;
import com.erp.dto.inventory.ItemBulkDiscountResultDTO;
import com.erp.dto.inventory.ItemBulkIdsRequestDTO;
import com.erp.dto.inventory.ItemBulkStatusRequestDTO;
import com.erp.dto.inventory.ItemCreateDTO;
import com.erp.dto.inventory.ItemCsvImportResultDTO;
import com.erp.dto.inventory.ItemResponseDTO;
import com.erp.dto.inventory.ItemStockAdjustDTO;
import com.erp.dto.inventory.ItemStockReceiveDTO;
import com.erp.dto.inventory.ItemUpdateDTO;
import com.erp.dto.inventory.ItemWarehouseStockRowDTO;
import com.erp.dto.inventory.StockBatchResponseDTO;
import com.erp.dto.history.BulkActionResultDTO;
import com.erp.security.context.AuthContext;
import com.erp.service.file.FileStorageService;
import com.erp.service.inventory.ItemCsvImportService;
import com.erp.service.inventory.ItemService;
import com.erp.service.inventory.ItemWarehouseStockService;
import com.erp.service.inventory.StockBatchService;
import com.erp.service.security.annotation.RequiresPermission;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/inventory/items")
public class ItemController {

    private final ItemService service;
    private final ItemCsvImportService csvImportService;
    private final ItemWarehouseStockService itemWarehouseStockService;
    private final StockBatchService stockBatchService;
    private final AuthContext auth;

    public ItemController(
            ItemService service,
            ItemCsvImportService csvImportService,
            FileStorageService fileStorageService,
            ItemWarehouseStockService itemWarehouseStockService,
            StockBatchService stockBatchService,
            AuthContext auth
    ) {
        this.service = service;
        this.csvImportService = csvImportService;
        this.itemWarehouseStockService = itemWarehouseStockService;
        this.stockBatchService = stockBatchService;
        this.auth = auth;
    }

    @RequiresPermission(module = AppModule.INVENTORY_ITEM, action = {AppAction.CREATE})
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ItemResponseDTO createItem(
            @RequestPart("data") ItemCreateDTO dto,
            @RequestPart(value = "image", required = false) MultipartFile image
    ) {
        return service.create(dto, image);
    }

    @RequiresPermission(module = AppModule.INVENTORY_ITEM, action = {AppAction.CREATE})
    @PostMapping(value = "/import-csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ItemCsvImportResultDTO importCsv(@RequestPart("file") MultipartFile file) {
        return csvImportService.importCsv(file);
    }

    @RequiresPermission(module = AppModule.INVENTORY_ITEM, action = {AppAction.EDIT})
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ItemResponseDTO updateJson(
            @PathVariable("id") Long id,
            @RequestBody ItemUpdateDTO dto
    ) {
        return service.update(id, dto, null);
    }

    @RequiresPermission(module = AppModule.INVENTORY_ITEM, action = {AppAction.EDIT})
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ItemResponseDTO updateMultipart(
            @PathVariable("id") Long id,
            @RequestPart("data") ItemUpdateDTO dto,
            @RequestPart(value = "image", required = false) MultipartFile image
    ) {
        return service.update(id, dto, image);
    }

    @RequiresPermission(module = AppModule.INVENTORY_ITEM, action = {AppAction.EDIT})
    @PostMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ItemResponseDTO updateItemImage(
            @PathVariable("id") Long id,
            @RequestPart("image") MultipartFile image
    ) {
        return service.updateItemImage(id, image);
    }

    @RequiresPermission(module = AppModule.INVENTORY_ITEM, action = {AppAction.EDIT})
    @PostMapping("/bulk-discount")
    public ItemBulkDiscountResultDTO applyBulkDiscount(@RequestBody ItemBulkDiscountRequestDTO req) {
        return service.applyBulkDiscount(req);
    }

    @RequiresPermission(module = AppModule.INVENTORY_ITEM, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping
    public List<ItemResponseDTO> list() {
        return service.listForCompany();
    }

    @RequiresPermission(module = AppModule.INVENTORY_STOCK, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping("/stock-catalog")
    public List<ItemResponseDTO> stockCatalog(
            @RequestParam(defaultValue = "false") boolean archived
    ) {
        return service.listStockCatalogForCompany(archived);
    }

    @RequiresPermission(module = AppModule.INVENTORY_ITEM, action = {AppAction.EDIT})
    @PostMapping("/bulk-archive")
    public BulkActionResultDTO bulkArchive(@RequestBody ItemBulkIdsRequestDTO req) {
        return service.bulkArchive(req);
    }

    @RequiresPermission(module = AppModule.INVENTORY_ITEM, action = {AppAction.EDIT})
    @PostMapping("/bulk-restore")
    public BulkActionResultDTO bulkRestore(@RequestBody ItemBulkIdsRequestDTO req) {
        return service.bulkRestore(req);
    }

    @RequiresPermission(module = AppModule.INVENTORY_ITEM, action = {AppAction.DELETE})
    @PostMapping("/bulk-delete")
    public BulkActionResultDTO bulkDelete(@RequestBody ItemBulkIdsRequestDTO req) {
        return service.bulkDelete(req);
    }

    @RequiresPermission(module = AppModule.INVENTORY_ITEM, action = {AppAction.EDIT})
    @PostMapping("/bulk-status")
    public BulkActionResultDTO bulkStatus(@RequestBody ItemBulkStatusRequestDTO req) {
        return service.bulkUpdateStatus(req);
    }

    @RequiresPermission(module = AppModule.INVENTORY_ITEM, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping("/{id}")
    public ItemResponseDTO get(@PathVariable("id") Long id) {
        return service.getItem(id);
    }

    @RequiresPermission(module = AppModule.INVENTORY_STOCK, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping("/{id}/warehouse-stock")
    public List<ItemWarehouseStockRowDTO> listWarehouseStock(@PathVariable("id") Long id) {
        return itemWarehouseStockService.listStockForItem(id, auth.getCurrentCompanyId());
    }

    @RequiresPermission(module = AppModule.INVENTORY_STOCK, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping("/{id}/batches")
    public List<StockBatchResponseDTO> listBatches(
            @PathVariable("id") Long id,
            @RequestParam(required = false) Long warehouseId
    ) {
        return stockBatchService.listBatchesForItem(id, warehouseId, auth.getCurrentCompanyId());
    }

    @RequiresPermission(module = AppModule.INVENTORY_STOCK, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping("/{id}/batch-movements")
    public com.erp.dto.inventory.StockBatchMovementReportDTO listBatchMovements(
            @PathVariable("id") Long id,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer limit,
            @RequestParam(defaultValue = "false") boolean archived
    ) {
        int resolvedSize = limit != null ? limit : size;
        return stockBatchService.buildMovementReport(
                auth.getCurrentCompanyId(),
                warehouseId,
                id,
                page,
                resolvedSize,
                archived
        );
    }

    @RequiresPermission(module = AppModule.INVENTORY_STOCK, action = {AppAction.CREATE, AppAction.EDIT})
    @PostMapping("/{id}/stock/receive")
    public ItemResponseDTO receiveStock(
            @PathVariable("id") Long id,
            @RequestBody ItemStockReceiveDTO dto
    ) {
        return service.receiveStock(id, dto);
    }

    @RequiresPermission(module = AppModule.INVENTORY_STOCK, action = {AppAction.EDIT})
    @PostMapping("/{id}/stock/adjust")
    public ItemResponseDTO adjustStock(
            @PathVariable("id") Long id,
            @RequestBody ItemStockAdjustDTO dto
    ) {
        return service.adjustStock(id, dto);
    }
}
