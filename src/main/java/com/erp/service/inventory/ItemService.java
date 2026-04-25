package com.erp.service.inventory;

import com.erp.domain.User;
import com.erp.domain.hr.Company;
import com.erp.domain.inventory.Item;
import com.erp.domain.inventory.ItemWarehouseStock;
import com.erp.domain.inventory.Warehouse;
import com.erp.dto.file.FileCategory;
import com.erp.dto.file.FileUploadResult;
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

import java.time.Instant;
import java.util.List;

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

    public ItemService(
            ItemRepository itemRepo,
            UserRepository userRepo,
            CompanyRepository companyRepo,
            AuthContext auth,
            WarehouseRepository warehouseRepo,
            FileStorageService fileStorageService,
            ItemWarehouseStockService itemWarehouseStockService
    ) {
        this.itemRepo = itemRepo;
        this.userRepo = userRepo;
        this.companyRepo = companyRepo;
        this.auth = auth;
        this.warehouseRepo = warehouseRepo;
        this.fileStorageService = fileStorageService;
        this.itemWarehouseStockService = itemWarehouseStockService;
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
                .costPrice(dto.getCostPrice())
                .sellingPrice(dto.getSellingPrice())
                .unitMeasure(dto.getUnitMeasure())
                .reorderLevel(dto.getReorderLevel())
                .status(dto.getStatus())
                .description(dto.getDescription())
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
                    true
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

        item.setName(dto.getName());
        item.setCategory(dto.getCategory());
        item.setSubCategory(dto.getSubCategory());
        item.setBrand(dto.getBrand());
        item.setLocation(dto.getLocation());
        item.setQuantity(dto.getQuantity());
        item.setMinimum(dto.getMinimum());
        item.setMaximum(dto.getMaximum());
        item.setCostPrice(dto.getCostPrice());
        item.setSellingPrice(dto.getSellingPrice());
        item.setStatus(dto.getStatus());
        // Preserve existing image on normal updates unless an explicit value is provided.
        if (dto.getImageUrl() != null) {
            item.setImageUrl(dto.getImageUrl());
        }
        item.setDescription(dto.getDescription());
        item.setUpdatedBy(user);
        item.setWarehouse(warehouse);
        item.setUpdatedAt(Instant.now());

        Item saved = itemRepo.save(item);

        if (dto.getQuantity() != null) {
            int newTotal = dto.getQuantity();
            int delta = newTotal - oldTotalQty;
            if (delta != 0) {
                itemWarehouseStockService.applyDeltaToDefaultWarehouse(saved, delta);
            }
        }
        itemWarehouseStockService.syncItemAggregates(saved);

        if (image != null && !image.isEmpty()) {
            FileUploadResult upload = fileStorageService.upload(
                    image,
                    FileCategory.INVENTORY_IMAGE,
                    saved.getId().toString(),
                    true
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
                true
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
        return itemRepo.findByCompanyId(auth.getCurrentCompanyId())
                .stream()
                .map(this::toDTO)
                .toList();
    }

    public ItemResponseDTO getItem(Long id) {
        Item item = getItemEntity(id);
        return toDTO(item);
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
        itemWarehouseStockService.addIncomingStock(
                item.getId(),
                whId,
                dto.getQuantityReceived(),
                auth.getCurrentCompanyId());

        item = itemRepo.findById(item.getId()).orElseThrow();

        if (dto.getCostPrice() != null) {
            item.setCostPrice(dto.getCostPrice());
        }
        if (dto.getUnitPrice() != null) {
            item.setSellingPrice(dto.getUnitPrice());
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

        itemWarehouseStockService.adjustRowToAbsoluteQuantity(
                item.getId(),
                whId,
                newRowQty,
                auth.getCurrentCompanyId());

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
                .category(item.getCategory())
                .subCategory(item.getSubCategory())
                .brand(item.getBrand())
                .quantity(item.getQuantity())
                .available(item.getAvailable())
                .reserved(item.getReserved())
                .costPrice(item.getCostPrice())
                .sellingPrice(item.getSellingPrice())
                .status(item.getStatus())
                .imageUrl(imageUrl)
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

}
