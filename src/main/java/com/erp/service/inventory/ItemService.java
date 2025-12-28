package com.erp.service.inventory;

import com.erp.domain.User;
import com.erp.domain.hr.Company;
import com.erp.domain.inventory.Item;
import com.erp.domain.inventory.Warehouse;
import com.erp.dto.inventory.ItemCreateDTO;
import com.erp.dto.inventory.ItemResponseDTO;
import com.erp.dto.inventory.ItemUpdateDTO;
import com.erp.repo.UserRepository;
import com.erp.repo.hr.CompanyRepository;
import com.erp.repo.inventory.ItemRepository;
import com.erp.repo.inventory.WarehouseRepository;
import com.erp.security.context.AuthContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public ItemService(
            ItemRepository itemRepo,
            UserRepository userRepo,
            CompanyRepository companyRepo,
            AuthContext auth,
            WarehouseRepository warehouseRepo
    ) {
        this.itemRepo = itemRepo;
        this.userRepo = userRepo;
        this.companyRepo = companyRepo;
        this.auth = auth;
        this.warehouseRepo = warehouseRepo;
    }

    // --------------------------
    // Create Item
    // --------------------------
    public ItemResponseDTO create(ItemCreateDTO dto) {

        Long companyId = auth.getCurrentCompanyId();
        Long userId = auth.getCurrentUserId();

        if (itemRepo.existsBySkuAndCompanyId(dto.getSku(), companyId)) {
            throw new RuntimeException("SKU already exists for this company");
        }

        Company company = companyRepo.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company not found"));

        Warehouse warehouse = warehouseRepo.findById(dto.getWarehouse())
                .orElseThrow(() -> new RuntimeException("Warehouse not found"));

        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

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
                .imageUrl(dto.getImageUrl())
                .description(dto.getDescription())
                .company(company)
                .warehouse(warehouse)
                .createdBy(user)
                .updatedBy(user)
                .createdAt(Instant.now())
                .build();

        return toDTO(itemRepo.save(item));
    }

    // --------------------------
    // Update Item
    // --------------------------
    public ItemResponseDTO update(Long id, ItemUpdateDTO dto) {

        Item item = getItemEntity(id); // 🔒 company check here

        User user = userRepo.findById(auth.getCurrentUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        Warehouse warehouse = warehouseRepo.findById(dto.getWarehouse())
                .orElseThrow(() -> new RuntimeException("Warehouse not found"));

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
        item.setImageUrl(dto.getImageUrl());
        item.setDescription(dto.getDescription());
        item.setUpdatedBy(user);
        item.setWarehouse(warehouse);
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
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .warehouse_id(item.getWarehouse().getId())
                .warehouse_name(item.getWarehouse().getName())
                .warehouse_location(item.getWarehouse().getLocation())
                .build();
    }

}
