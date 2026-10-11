package com.erp.repo.admin;

import com.erp.domain.admin.KnowledgeBaseItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KnowledgeBaseItemRepository extends JpaRepository<KnowledgeBaseItem, Long> {
    List<KnowledgeBaseItem> findAllByOrderByCreatedAtDesc();
}
