package com.menuonline.payloads;

import java.math.BigDecimal;
import java.util.List;

public record CreateProductRequest(
        String name,
        String description,
        List<PriceRequest> prices,
        List<Long> tagsIds,
        boolean newProduct,
        boolean bestSelling,
        long categoryId,
        boolean active) {

    public static record PriceRequest(
            Long id,
            BigDecimal value,
            String unit) {
    }
}
