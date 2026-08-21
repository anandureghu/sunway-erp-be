package com.erp.service.inventory;

import com.erp.domain.User;
import com.erp.domain.hr.Company;
import com.erp.domain.inventory.Item;
import com.erp.domain.inventory.ItemWarehouseStock;
import com.erp.domain.inventory.StockBatchSourceType;
import com.erp.domain.inventory.Warehouse;
import com.erp.dto.file.FileCategory;
import com.erp.dto.file.FileUploadResult;
import com.erp.dto.inventory.ItemBulkDiscountRequestDTO;
import com.erp.dto.inventory.ItemBulkDiscountResultDTO;
import com.erp.dto.inventory.ItemCreateDTO;
import com.erp.dto.inventory.ItemResponseDTO;
import com.erp.dto.inventory.ItemStockAdjustDTO;
import com.erp.dto.inventory.ItemStockReceiveDTO;
import com.erp.dto.inventory.ItemUpdateDTO;
import com.erp.repo.UserRepository;
import com.erp.repo.hr.CompanyRepository;
import com.erp.repo.inventory.ItemRepository;
import com.erp.repo.inventory.WarehouseRepository;
import com.erp.security.context.AuthContext;
import com.erp.service.file.FileStorageService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@Transactional
public class ItemService {


    private final ItemRepository itemRepo;
    private final UserRepository userRepo;
    private final CompanyRepository companyRepo;
    private final WarehouseRepository warehouseRepo;
    private final AuthContext auth;
    private final FileStorageService fileStorageService;
    private final ItemWarehouseStockService itemWarehouseStockService;
    private final StockBatchService stockBatchService;

    public ItemService(
            ItemRepository itemRepo,
            UserRepository userRepo,
            CompanyRepository companyRepo,
            AuthContext auth,
            WarehouseRepository warehouseRepo,
            FileStorageService fileStorageService,
            ItemWarehouseStockService itemWarehouseStockService,
            StockBatchService stockBatchService
    ) {
        this.itemRepo = itemRepo;
        this.userRepo = userRepo;
        this.companyRepo = companyRepo;
        this.auth = auth;
        this.warehouseRepo = warehouseRepo;
        this.fileStorageService = fileStorageService;
        this.itemWarehouseStockService = itemWarehouseStockService;
        this.stockBatchService = stockBatchService;
    }

    // --------------------------
    // Create Item
    // --------------------------
    @Transactional
    public ItemResponseDTO create(ItemCreateDTO dto, MultipartFile image) {

        Long companyId = auth.getCurrentCompanyId();
        Long userId = auth.getCurrentUserId();

        Company company = companyRepo.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company not found"));

        Warehouse warehouse = warehouseRepo.findById(dto.getWarehouse())
                .orElseThrow(() -> new RuntimeException("Warehouse not found"));

        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 1️⃣ Save item FIRST (no image)
        Item item = Item.builder()
                .sku(dto.getSku())
                .name(dto.getName())
                .type(dto.getType())
                .category(dto.getCategory())
                .subCategory(dto.getSubCategory())
                .brand(dto.getBrand())
                .location(dto.getLocation())
                .quantity(dto.getQuantity())
                .available(dto.getQuantity())
                .reserved(0)
                .minimum(dto.getMinimum())
                .maximum(dto.getMaximum())
                .barcode(dto.getBarcode())
                .serialNo(dto.getSerialNo())
                .dateReceived(resolveInitialDateReceived(dto))
                .expiryDate(parseOptionalDate(dto.getExpiryDate()))
                .costPrice(dto.getCostPrice())
                .sellingPrice(dto.getSellingPrice())
                .unitSale(dto.getUnitSale())
                .unitMeasure(dto.getUnitMeasure())
                .reorderLevel(dto.getReorderLevel())
                .status(dto.getStatus())
                .description(dto.getDescription())
                .metadata(dto.getMetadata() != null && !dto.getMetadata().isBlank() ? dto.getMetadata().trim() : null)
                .company(company)
                .warehouse(warehouse)
                .createdBy(user)
                .updatedBy(user)
                .createdAt(Instant.now())
                .build();

        Item saved = itemRepo.save(item);
        itemWarehouseStockService.ensureInitialRowForNewItem(saved);

        // 2️⃣ Upload image AFTER item exists
        if (image != null && !image.isEmpty()) {

            FileUploadResult upload = fileStorageService.upload(
                    image,
                    FileCategory.INVENTORY_IMAGE,
                    saved.getId().toString(),
                    true,
                    saved.getCompany().getId()
            );

            saved.setImageUrl(upload.getBlobPath());

            itemRepo.save(saved);
        }

        return toDTO(saved);
    }

    // --------------------------
    // Update Item
    // --------------------------
    public ItemResponseDTO update(Long id, ItemUpdateDTO dto, MultipartFile image) {

        Item item = getItemEntity(id); // 🔒 company check here

        User user = userRepo.findById(auth.getCurrentUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        Warehouse warehouse = warehouseRepo.findById(dto.getWarehouse())
                .orElseThrow(() -> new RuntimeException("Warehouse not found"));

        int oldTotalQty = item.getQuantity() == null ? 0 : item.getQuantity();

        if (dto.getSku() != null && !dto.getSku().isBlank()) item.setSku(dto.getSku().toUpperCase());
        item.setName(dto.getName());
        item.setType(dto.getType());
        item.setCategory(dto.getCategory());
        item.setSubCategory(dto.getSubCategory());
        item.setBrand(dto.getBrand());
        item.setLocation(dto.getLocation());
        item.setQuantity(dto.getQuantity());
        item.setMinimum(dto.getMinimum());
        item.setMaximum(dto.getMaximum());
        if (dto.getReorderLevel() != null) item.setReorderLevel(dto.getReorderLevel());
        item.setBarcode(dto.getBarcode());
        item.setSerialNo(dto.getSerialNo());
        if (dto.getDateReceived() != null) {
            item.setDateReceived(parseOptionalDate(dto.getDateReceived()));
        }
        if (dto.getExpiryDate() != null) {
            item.setExpiryDate(parseOptionalDate(dto.getExpiryDate()));
        }
        if (dto.getUnitMeasure() != null) item.setUnitMeasure(dto.getUnitMeasure());
        item.setCostPrice(dto.getCostPrice());
        item.setSellingPrice(dto.getSellingPrice());
        item.setUnitSale(dto.getUnitSale());
        item.setStatus(dto.getStatus());
        // Preserve existing image on normal updates unless an explicit value is provided.
        if (dto.getImageUrl() != null) {
            item.setImageUrl(dto.getImageUrl());
        }
        item.setDescription(dto.getDescription());
        if (dto.getMetadata() != null) {
            item.setMetadata(dto.getMetadata().isBlank() ? null : dto.getMetadata().trim());
        }
        item.setUpdatedBy(user);
        item.setWarehouse(warehouse);
        item.setUpdatedAt(Instant.now());

        Item saved = itemRepo.save(item);

        if (dto.getQuantity() != null) {
            int newTotal = dto.getQuantity();
            int delta = newTotal - oldTotalQty;
            if (delta != 0) {
                Long whId = saved.getWarehouse().getId();
                Long companyId = auth.getCurrentCompanyId();
                if (delta > 0) {
                    stockBatchService.receiveIntoBatch(
                            saved.getId(),
                            whId,
                            delta,
                            saved.getCostPrice() != null ? saved.getCostPrice() : java.math.BigDecimal.ZERO,
                            "ADJ-" + java.time.LocalDate.now(),
                            null,
                            StockBatchSourceType.ADJUSTMENT,
                            null,
                            companyId
                    );
                } else {
                    stockBatchService.syncBatchesToMatchIws(saved.getId(), whId, companyId);
                    stockBatchService.consumeFifo(
                            saved.getId(),
                            whId,
                            Math.abs(delta),
                            "ITEM_QTY_EDIT",
                            saved.getId(),
                            companyId,
                            com.erp.domain.inventory.StockBatchMovementType.ADJUSTMENT
                    );
                }
            }
        }
        itemWarehouseStockService.syncItemAggregates(saved);

        if (image != null && !image.isEmpty()) {
            FileUploadResult upload = fileStorageService.upload(
                    image,
                    FileCategory.INVENTORY_IMAGE,
                    saved.getId().toString(),
                    true,
                    saved.getCompany().getId()
            );
            saved.setImageUrl(upload.getBlobPath());
            saved = itemRepo.save(saved);
        }

        return toDTO(saved);
    }

    public ItemResponseDTO updateItemImage(Long id, MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new IllegalArgumentException("Image is required");
        }
        Item item = getItemEntity(id);
        User user = userRepo.findById(auth.getCurrentUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        FileUploadResult upload = fileStorageService.upload(
                image,
                FileCategory.INVENTORY_IMAGE,
                item.getId().toString(),
                true,
                item.getCompany().getId()
        );
        item.setImageUrl(upload.getBlobPath());
        item.setUpdatedBy(user);
        item.setUpdatedAt(Instant.now());

        return toDTO(itemRepo.save(item));
    }


    // --------------------------
    // List
    // --------------------------
    public List<ItemResponseDTO> listForCompany() {
        return itemRepo.findByCompanyIdOrderByCreatedAtDesc(auth.getCurrentCompanyId())
                .stream()
                .map(this::toDTO)
                .toList();
    }

    /**
     * One catalog row per item×warehouse stock line (quantities match sales availability checks).
     */
    public List<ItemResponseDTO> listStockCatalogForCompany() {
        Long companyId = auth.getCurrentCompanyId();
        return itemWarehouseStockService.listStockCatalog(companyId).stream()
                .map(row -> toDTOForWarehouse(row.item(), row.warehouse(), row.quantityOnHand(), row.reserved(), row.available()))
                .toList();
    }

    public ItemResponseDTO getItem(Long id) {
        Item item = getItemEntity(id);
        return toDTO(item);
    }

    /**
     * Apply a one-time percent discount to catalog selling prices for the given items.
     * Reduces {@code sellingPrice} and keeps {@code unitSale} in sync.
     */
    public ItemBulkDiscountResultDTO applyBulkDiscount(ItemBulkDiscountRequestDTO req) {
        if (req == null || req.getItemIds() == null || req.getItemIds().isEmpty()) {
            throw new IllegalArgumentException("Select at least one item to discount.");
        }
        BigDecimal pct = req.getDiscountPercent();
        if (pct == null || pct.compareTo(BigDecimal.ZERO) <= 0 || pct.compareTo(new BigDecimal("100")) >= 0) {
            throw new IllegalArgumentException("Discount percent must be greater than 0 and less than 100.");
        }

        User user = userRepo.findById(auth.getCurrentUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));
        Long companyId = auth.getCurrentCompanyId();

        Set<Long> uniqueIds = new LinkedHashSet<>(req.getItemIds());
        BigDecimal factor = BigDecimal.ONE.subtract(
                pct.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP));

        int updated = 0;
        List<Item> toSave = new ArrayList<>();
        Instant now = Instant.now();
        for (Long id : uniqueIds) {
            if (id == null) continue;
            Item item = itemRepo.findById(id)
                    .filter(i -> i.getCompany() != null && companyId.equals(i.getCompany().getId()))
                    .orElse(null);
            if (item == null) continue;

            BigDecimal current = item.getSellingPrice();
            if (current == null || current.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal discounted = current.multiply(factor).setScale(2, RoundingMode.HALF_UP);
            if (discounted.compareTo(BigDecimal.ZERO) < 0) {
                discounted = BigDecimal.ZERO;
            }
            item.setSellingPrice(discounted);
            item.setUnitSale(discounted);
            item.setUpdatedBy(user);
            item.setUpdatedAt(now);
            toSave.add(item);
            updated++;
        }

        if (!toSave.isEmpty()) {
            itemRepo.saveAll(toSave);
        }

        return ItemBulkDiscountResultDTO.builder()
                .requestedCount(uniqueIds.size())
                .updatedCount(updated)
                .discountPercent(pct)
                .build();
    }

    // --------------------------
    // Stock movements
    // --------------------------

    public ItemResponseDTO receiveStock(Long id, ItemStockReceiveDTO dto) {
        Item item = getItemEntity(id);
        User user = userRepo.findById(auth.getCurrentUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (dto.getQuantityReceived() == null || dto.getQuantityReceived() <= 0) {
            throw new IllegalArgumentException("quantityReceived must be positive");
        }

        Long whId = dto.getWarehouseId() != null ? dto.getWarehouseId() : item.getWarehouse().getId();
        Long companyId = auth.getCurrentCompanyId();

        stockBatchService.receiveIntoBatch(
                item.getId(),
                whId,
                dto.getQuantityReceived(),
                dto.getCostPrice() != null ? dto.getCostPrice() : item.getCostPrice(),
                dto.getBatchNo(),
                dto.getExpiryDate() != null ? parseOptionalDate(dto.getExpiryDate()) : null,
                StockBatchSourceType.DIRECT_RECEIVE,
                null,
                companyId
        );

        item = itemRepo.findById(item.getId()).orElseThrow();

        if (dto.getUnitPrice() != null) {
            item.setSellingPrice(dto.getUnitPrice());
        }

        item.setDateReceived(resolveReceiveDate(dto.getReceivedDate()));
        if (dto.getExpiryDate() != null) {
            item.setExpiryDate(parseOptionalDate(dto.getExpiryDate()));
        }
        if (dto.getSerialNo() != null && !dto.getSerialNo().isBlank()) {
            item.setSerialNo(dto.getSerialNo());
        }

        item.setUpdatedBy(user);
        item.setUpdatedAt(Instant.now());

        return toDTO(itemRepo.save(item));
    }

    public ItemResponseDTO adjustStock(Long id, ItemStockAdjustDTO dto) {
        Item item = getItemEntity(id);
        User user = userRepo.findById(auth.getCurrentUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        Long whId = dto.getWarehouseId() != null ? dto.getWarehouseId() : item.getWarehouse().getId();

        Warehouse targetWh = warehouseRepo.findById(whId)
                .orElseThrow(() -> new RuntimeException("Warehouse not found"));
        if (!targetWh.getCompany().getId().equals(item.getCompany().getId())) {
            throw new IllegalArgumentException("Warehouse does not belong to this company");
        }

        ItemWarehouseStock stockRow = itemWarehouseStockService.getOrCreateStockRow(item, targetWh);
        int currentOnHand = stockRow.getQuantityOnHand() == null ? 0 : stockRow.getQuantityOnHand();

        int newRowQty;
        if (dto.getNewQuantity() != null) {
            newRowQty = dto.getNewQuantity();
        } else if (dto.getAdjustmentQuantity() != null) {
            newRowQty = currentOnHand + dto.getAdjustmentQuantity();
        } else {
            throw new IllegalArgumentException("Either newQuantity or adjustmentQuantity is required");
        }

        if (newRowQty < 0) {
            throw new IllegalArgumentException("Resulting quantity cannot be negative");
        }

        if (dto.getReason() == null || dto.getReason().isBlank()) {
            throw new IllegalArgumentException("Reason is required");
        }

        Long companyId = auth.getCurrentCompanyId();
        int delta = newRowQty - currentOnHand;
        if (delta > 0) {
            stockBatchService.receiveIntoBatch(
                    item.getId(),
                    whId,
                    delta,
                    item.getCostPrice() != null ? item.getCostPrice() : java.math.BigDecimal.ZERO,
                    "ADJ-" + java.time.LocalDate.now(),
                    null,
                    StockBatchSourceType.ADJUSTMENT,
                    null,
                    companyId
            );
        } else if (delta < 0) {
            stockBatchService.syncBatchesToMatchIws(item.getId(), whId, companyId);
            stockBatchService.consumeFifo(
                    item.getId(),
                    whId,
                    Math.abs(delta),
                    "STOCK_ADJUSTMENT",
                    item.getId(),
                    companyId,
                    com.erp.domain.inventory.StockBatchMovementType.ADJUSTMENT
            );
        }

        item = itemRepo.findById(item.getId()).orElseThrow();
        item.setUpdatedBy(user);
        item.setUpdatedAt(Instant.now());

        return toDTO(itemRepo.save(item));
    }

    // --------------------------
    // Helpers
    // --------------------------
    private Item getItemEntity(Long id) {

        Long companyId = auth.getCurrentCompanyId();

        return itemRepo.findById(id)
                .filter(item -> item.getCompany().getId().equals(companyId))
                .orElseThrow(() -> new AccessDeniedException(
                        "Item does not belong to your company"
                ));
    }

    private ItemResponseDTO toDTO(Item item) {
        String imageUrl = fileStorageService.getPublicUrl(item.getImageUrl());

        return ItemResponseDTO.builder()
                .id(item.getId())
                .sku(item.getSku())
                .name(item.getName())
                .type(item.getType())
                .category(item.getCategory())
                .subCategory(item.getSubCategory())
                .brand(item.getBrand())
                .description(item.getDescription())
                .unitMeasure(item.getUnitMeasure())
                .barcode(item.getBarcode())
                .serialNo(item.getSerialNo())
                .dateReceived(item.getDateReceived())
                .expiryDate(item.getExpiryDate())
                .location(item.getLocation())
                .quantity(item.getQuantity())
                .available(item.getAvailable())
                .reserved(item.getReserved())
                .minimum(item.getMinimum())
                .maximum(item.getMaximum())
                .reorderLevel(item.getReorderLevel())
                .costPrice(item.getCostPrice())
                .sellingPrice(item.getSellingPrice())
                .unitSale(item.getUnitSale())
                .status(item.getStatus())
                .imageUrl(imageUrl)
                .metadata(item.getMetadata())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .warehouse_id(item.getWarehouse().getId())
                .warehouse_name(item.getWarehouse().getName())
                .warehouse_location(
                        String.format(
                                "%s, %s, %s",
                                item.getWarehouse().getStreet(),
                                item.getWarehouse().getCity(),
                                item.getWarehouse().getCountry()
                        )
                ).build();
    }

    private ItemResponseDTO toDTOForWarehouse(
            Item item,
            Warehouse warehouse,
            int quantityOnHand,
            int reserved,
            int available
    ) {
        String imageUrl = fileStorageService.getPublicUrl(item.getImageUrl());
        return ItemResponseDTO.builder()
                .id(item.getId())
                .sku(item.getSku())
                .name(item.getName())
                .type(item.getType())
                .category(item.getCategory())
                .subCategory(item.getSubCategory())
                .brand(item.getBrand())
                .description(item.getDescription())
                .unitMeasure(item.getUnitMeasure())
                .barcode(item.getBarcode())
                .serialNo(item.getSerialNo())
                .dateReceived(item.getDateReceived())
                .expiryDate(item.getExpiryDate())
                .location(item.getLocation())
                .quantity(quantityOnHand)
                .available(available)
                .reserved(reserved)
                .minimum(item.getMinimum())
                .maximum(item.getMaximum())
                .reorderLevel(item.getReorderLevel())
                .costPrice(item.getCostPrice())
                .sellingPrice(item.getSellingPrice())
                .unitSale(item.getUnitSale())
                .status(item.getStatus())
                .imageUrl(imageUrl)
                .metadata(item.getMetadata())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .warehouse_id(warehouse.getId())
                .warehouse_name(warehouse.getName())
                .warehouse_location(
                        String.format(
                                "%s, %s, %s",
                                warehouse.getStreet(),
                                warehouse.getCity(),
                                warehouse.getCountry()
                        )
                ).build();
    }

    private LocalDate resolveReceiveDate(String receivedDate) {
        LocalDate parsed = parseOptionalDate(receivedDate);
        return parsed != null ? parsed : LocalDate.now();
    }

    private LocalDate resolveInitialDateReceived(ItemCreateDTO dto) {
        if (dto.getQuantity() == null || dto.getQuantity() <= 0) {
            return parseOptionalDate(dto.getDateReceived());
        }
        return resolveReceiveDate(dto.getDateReceived());
    }

    private LocalDate parseOptionalDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDate.parse(value);
    }

}
