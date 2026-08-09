package io.github.heybadrinath.reconriver.shared.api;

import java.util.List;

public record PageResponse<T>(
        List<T> items, int page, int size, long totalElements, int totalPages) {
    public static <T> PageResponse<T> of(List<T> items, int page, int size, long total) {
        int pages = size == 0 ? 0 : (int) Math.ceil((double) total / size);
        return new PageResponse<>(items, page, size, total, pages);
    }
}
