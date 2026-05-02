package com.erp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.config.PageableHandlerMethodArgumentResolverCustomizer;
import org.springframework.data.web.config.SortHandlerMethodArgumentResolverCustomizer;

/**
 * Default list ordering when the client does not send {@code sort}: newest first by {@code createdAt}.
 * Paginated endpoints using {@link org.springframework.data.domain.Pageable} pick this up automatically.
 */
@Configuration
public class PageableWebConfiguration {

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    @Bean
    public SortHandlerMethodArgumentResolverCustomizer sortResolverCustomizer() {
        return resolver -> resolver.setFallbackSort(DEFAULT_SORT);
    }

    @Bean
    public PageableHandlerMethodArgumentResolverCustomizer pageableResolverCustomizer(
            @Value("${spring.data.web.pageable.default-page-size:20}") int defaultPageSize) {
        return resolver -> resolver.setFallbackPageable(
                PageRequest.of(0, defaultPageSize, DEFAULT_SORT));
    }
}
