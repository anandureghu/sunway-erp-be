package com.erp.dto.inventory;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CategoryResponseDTO {
    private Long id;
    private String code;
    private String name;
    private String status;
    private Long parentId;
    private List<CategoryResponseDTO> subCategories;
}
