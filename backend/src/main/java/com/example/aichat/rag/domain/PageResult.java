package com.example.aichat.rag.domain;

import java.util.List;
import java.util.function.Function;

/**
 * One page of results.
 */
public record PageResult<T>(List<T> items, int page, int size, long total) {

    public PageResult {
        items = List.copyOf(items);
    }

    public int totalPages() {
        return size == 0 ? 0 : (int) ((total + size - 1) / size);
    }

    public <R> PageResult<R> map(Function<T, R> mapper) {
        return new PageResult<>(items.stream().map(mapper).toList(), page, size, total);
    }
}
