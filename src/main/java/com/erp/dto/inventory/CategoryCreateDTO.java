package com.erp.dto.inventory;

import lombok.Data;

@Data
public class CategoryCreateDTO {
    private String code;
    private String name;
    private String status;
    private Long parentId; // null = category, not null = subcategory
}
