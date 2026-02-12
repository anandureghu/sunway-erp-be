package com.erp.repo.inventory;

import com.erp.domain.inventory.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findByCompanyIdAndParentIdIsNull(Long companyId); // categories

    List<Category> findByCompanyIdAndParentId(Long companyId, Long parentId); // subcategories

    @Query("""
                SELECT c
                FROM Category c
                LEFT JOIN FETCH c.subCategories sc
                WHERE c.id = :id
                  AND c.company.id = :companyId
            """)
    Optional<Category> findCategoryWithSubCategories(
            @Param("id") Long id,
            @Param("companyId") Long companyId
    );

    boolean existsByCompanyIdAndParentIdAndCode(
            Long companyId,
            Long parentId,
            String code
    );
}
