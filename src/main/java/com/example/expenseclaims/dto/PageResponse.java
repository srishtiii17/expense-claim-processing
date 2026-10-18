package com.example.expenseclaims.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * One page of results. Used instead of returning Spring's Page object directly, whose JSON shape is
 * an internal detail that may change between versions.
 *
 * @param content       the items on this page
 * @param page          page number, starting at 0
 * @param size          requested page size
 * @param totalElements number of matching items across all pages
 * @param totalPages    number of pages
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
