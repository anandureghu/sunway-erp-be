package com.erp.dto.common;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Stable JSON shape for paginated API responses ({@code content}, {@code totalElements}, …).
 * Use instead of returning {@link Page} directly from controllers.
 */
public record PagedResponse<T>(
        List<T> content,
        long totalElements,
        int totalPages,
        int size,
        int number
) {
    public static <T> PagedResponse<T> from(Page<T> page) {
        return new PagedResponse<>(
                page.getContent(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getSize(),
                page.getNumber()
        );
    }
}
