package com.erp.repo.inventory;

import com.erp.domain.inventory.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findByCompanyIdAndParentIsNull(Long companyId); // categories

    List<Category> findByCompanyIdAndParentId(Long companyId, Long parentId); // subcategories

    boolean existsByCompanyIdAndParentIdAndCode(
            Long companyId,
            Long parentId,
            String code
    );
}
