package com.erp.service.inventory;

import com.erp.domain.User;
import com.erp.domain.hr.Company;
import com.erp.domain.inventory.Category;
import com.erp.dto.inventory.CategoryCreateDTO;
import com.erp.dto.inventory.CategoryResponseDTO;
import com.erp.dto.inventory.CategoryUpdateDTO;
import com.erp.repo.UserRepository;
import com.erp.repo.hr.CompanyRepository;
import com.erp.repo.inventory.CategoryRepository;
import com.erp.security.context.AuthContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class CategoryService {

    private final CategoryRepository repo;
    private final CompanyRepository companyRepo;
    private final UserRepository userRepo;
    private final AuthContext auth;

    public CategoryService(
            CategoryRepository repo,
            CompanyRepository companyRepo,
            UserRepository userRepo,
            AuthContext auth
    ) {
        this.repo = repo;
        this.companyRepo = companyRepo;
        this.userRepo = userRepo;
        this.auth = auth;
    }

    // --------------------------
    // Create Category / Subcategory
    // --------------------------
    public CategoryResponseDTO create(CategoryCreateDTO dto) {

        Long companyId = auth.getCurrentCompanyId();
        Long userId = auth.getCurrentUserId();

        Category parent = null;
        if (dto.getParentId() != null) {
            parent = getEntity(dto.getParentId());
        }

        if (repo.existsByCompanyIdAndParentIdAndCode(
                companyId,
                dto.getParentId(),
                dto.getCode()
        )) {
            throw new RuntimeException("Category code already exists");
        }

        Company company = companyRepo.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company not found"));

        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Category category = Category.builder()
                .code(dto.getCode())
                .name(dto.getName())
                .status(dto.getStatus())
                .parent(parent)
                .company(company)
                .createdByUser(user)
                .updatedByUser(user)
                .build();

        return toDTO(repo.save(category));
    }

    // --------------------------
    // Update
    // --------------------------
    public CategoryResponseDTO update(Long id, CategoryUpdateDTO dto) {

        Category category = getEntity(id);

        User user = userRepo.findById(auth.getCurrentUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        category.setName(dto.getName());
        category.setStatus(dto.getStatus());
        category.setUpdatedByUser(user);

        return toDTO(repo.save(category));
    }

    // --------------------------
    // Get single
    // --------------------------
    public CategoryResponseDTO get(Long id) {
        Category category = repo
                .findCategoryWithSubCategories(id, auth.getCurrentCompanyId())
                .orElseThrow(() -> new RuntimeException("Category not found"));

        CategoryResponseDTO responseDTO = toDTO(category);
        responseDTO.setSubCategories(category.getSubCategories().stream().map(this::toDTO).toList());

        return responseDTO;
    }

    // --------------------------
    // List top-level categories
    // --------------------------
    public List<CategoryResponseDTO> listCategories() {
        return repo.findByCompanyIdAndParentIdIsNullOrderByCreatedAtDesc(auth.getCurrentCompanyId())
                .stream()
                .map(this::toDTO)
                .toList();
    }

    // --------------------------
    // List subcategories
    // --------------------------
    public List<CategoryResponseDTO> listSubCategories(Long parentId) {

        Category parent = getEntity(parentId);

        return repo.findByCompanyIdAndParentIdOrderByCreatedAtDesc(
                        auth.getCurrentCompanyId(),
                        parent.getId()
                )
                .stream()
                .map(this::toDTO)
                .toList();
    }

    // --------------------------
    // Delete
    // --------------------------
    public void delete(Long id) {

        Category category = getEntity(id);

        boolean hasChildren = !repo.findByCompanyIdAndParentIdOrderByCreatedAtDesc(
                auth.getCurrentCompanyId(),
                id
        ).isEmpty();

        if (hasChildren) {
            throw new RuntimeException("Cannot delete category with subcategories");
        }

        repo.delete(category);
    }

    // --------------------------
    // Helpers
    // --------------------------
    private Category getEntity(Long id) {
        return repo.findById(id)
                .filter(c -> c.getCompany().getId().equals(auth.getCurrentCompanyId()))
                .orElseThrow(() ->
                        new RuntimeException("Category not found or access denied")
                );
    }

    private CategoryResponseDTO toDTO(Category c) {
        return CategoryResponseDTO.builder()
                .id(c.getId())
                .code(c.getCode())
                .name(c.getName())
                .status(c.getStatus())
                .parentId(c.getParent() != null ? c.getParent().getId() : null)
                .subCategories(c.getSubCategories() != null ? c.getSubCategories().stream().map(this::toDTO).toList() : new ArrayList<>())
                .build();
    }
}
