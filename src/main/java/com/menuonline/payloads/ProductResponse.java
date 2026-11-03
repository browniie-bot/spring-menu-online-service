package com.menuonline.payloads;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.menuonline.entity.Product;

public record ProductResponse(
        long id,
        String name,
        String description,
        Boolean active,
        String image,
        List<PriceResponse> prices,
        boolean newProduct,
        boolean bestSelling,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        long categoryId,
        String categoryName,
        List<TagsResponse> tags) {

    public static ProductResponse from(Product product) {
        List<PriceResponse> prices = product.getPrices().stream()
                .map(p -> new PriceResponse(p.getId(), p.getValue(), p.getUnit()))
                .toList();
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.isActive(),
                product.getImage(),
                prices,
                product.isNewProduct(),
                product.isBestSelling(),
                product.getCreatedAt(),
                product.getUpdatedAt(),
                product.getCategory().getId(),
                product.getCategory().getName(),
                product.getTags().stream().map(t -> new TagsResponse(t.getId(), t.getName())).toList());
    }

    public static record PriceResponse(
            Long id,
            BigDecimal value,
            String unit) {
    }

    public static record TagsResponse(Long id, String name){
    }
}
