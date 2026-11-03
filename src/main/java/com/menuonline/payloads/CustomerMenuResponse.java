package com.menuonline.payloads;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.menuonline.entity.Customization;
import com.menuonline.entity.Schedule;
import com.menuonline.entity.UserEntity;
import com.menuonline.repository.ProductRepository.ProductMenuProjection;

import lombok.Builder;

@Builder
public record CustomerMenuResponse(
        EstablishmentInfoResponse info,
        CustomizationResponse customization,
        List<ProductResponse> bestSelling,
        List<ProductResponse> newProducts,
        List<ScheduleResponse> schedules,
        List<CategoryResponse> categories) {

    public static CustomerMenuResponse from(UserEntity user, List<Schedule> schedules,
            List<ProductMenuProjection> projection,
            Customization customization) {
        EstablishmentInfoResponse info = new EstablishmentInfoResponse(user.getId(),
                user.getEstablishmentName(), user.getEstablishmentDescription(), user.getInstagram(),
                user.getFacebook(), user.getWebsite(), user.getWhatsapp(), user.getWifiName(), user.getWifiPassword(),
                user.getPhone(), user.getImage(), user.getAddressLine(), user.getCode(), user.getCity());

        List<ScheduleResponse> scheduleResponses = ScheduleResponse.from(schedules);

        List<CategoryResponse> categories = new ArrayList<>();
        List<ProductResponse> bestSellings = buildBestSellingList(projection);
        List<ProductResponse> newProducts = buildNewProducts(projection);

        for (ProductMenuProjection proj : projection.stream().filter(p -> !p.isBestSelling() && !p.isNewProduct())
                .toList()) {
            Optional<CategoryResponse> category = categories.stream()
                    .filter(cat -> cat.id() == proj.getCategoryId())
                    .findFirst();
            if (category.isPresent()) {
                CategoryResponse cat = category.get();
                cat.products()
                        .stream()
                        .filter(p -> p.id() == proj.getId())
                        .findFirst()
                        .ifPresentOrElse(p -> {
                            incrementExitingProdct(proj, p);
                        }, () -> {
                            List<PriceResponse> prices = new ArrayList<>();
                            PriceResponse price = createPrice(proj);
                            prices.add(price);
                            cat.products().add(createProduct(proj, prices));
                        });
            } else {
                CategoryResponse cat = createCategory(proj);
                categories.add(cat);
            }

        }
        return CustomerMenuResponse.builder()
                .info(info)
                .bestSelling(bestSellings)
                .newProducts(newProducts)
                .customization(CustomizationResponse.from(customization))
                .schedules(scheduleResponses)
                .categories(categories)
                .build();
    }

    private static void incrementExitingProdct(ProductMenuProjection proj, ProductResponse p) {
        // PRICES
        p.prices().stream()
                .filter(pr -> pr.id() == proj.getPriceId().longValue())
                .findFirst()
                .ifPresentOrElse(price -> {
                }, () -> p.prices().add(createPrice(proj)));

        // TAGS
        if (proj.getTagName() != null) {
            p.tags().stream()
                    .filter(t -> t.id().equals(proj.getTagId()))
                    .findFirst()
                    .orElseGet(() -> {
                        p.tags().add(new TagsResponse(proj.getTagId(), proj.getTagName()));
                        return null;
                    });
        }
    }

    private static List<ProductResponse> buildNewProducts(List<ProductMenuProjection> projection) {
        List<ProductResponse> list = new ArrayList<>();
        for (var prod : projection.stream().filter(p -> p.isNewProduct()).toList()) {
            Optional<ProductResponse> product = list.stream()
                    .filter(p -> p.id() == prod.getId())
                    .findFirst();
            if (product.isPresent()) {
                incrementExitingProdct(prod, product.get());
            } else {
                // product do not exist in the list
                PriceResponse price = createPrice(prod);
                List<PriceResponse> prices = new ArrayList<>();
                prices.add(price);
                list.add(createProduct(prod, prices));
            }
        }
        return list;
    }

    private static List<ProductResponse> buildBestSellingList(List<ProductMenuProjection> projection) {
        List<ProductResponse> list = new ArrayList<>();
        for (var prod : projection.stream().filter(p -> p.isBestSelling()).toList()) {
            Optional<ProductResponse> product = list.stream()
                    .filter(p -> p.id() == prod.getId())
                    .findFirst();
            if (product.isPresent()) {
                incrementExitingProdct(prod, product.get());
            } else {
                // product do not exist in the list
                PriceResponse price = createPrice(prod);
                List<PriceResponse> prices = new ArrayList<>();
                prices.add(price);
                list.add(createProduct(prod, prices));
            }
        }
        return list;
    }

    private static CategoryResponse createCategory(ProductMenuProjection proj) {
        List<PriceResponse> prices = new ArrayList<>();
        PriceResponse price = createPrice(proj);
        prices.add(price);
        List<ProductResponse> products = new ArrayList<>();
        products.add(createProduct(proj, prices));
        CategoryResponse cat = new CategoryResponse(proj.getCategoryId(),
                proj.getCategoryName(), products);
        return cat;
    }

    private static PriceResponse createPrice(ProductMenuProjection proj) {
        PriceResponse price = new PriceResponse(proj.getPriceId(), proj.getValue(), proj.getUnit());
        return price;
    }

    private static ProductResponse createProduct(ProductMenuProjection proj, List<PriceResponse> prices) {
        List<TagsResponse> tags = new ArrayList<>();
        if (proj.getTagName() != null) {
            tags.add(new TagsResponse(proj.getTagId(), proj.getTagName()));
        }
        ProductResponse product = new ProductResponse(proj.getId(),
                proj.getProductName(),
                proj.getImage(),
                proj.getDescription(),
                proj.isNewProduct(),
                proj.isBestSelling(),
                proj.getCategoryName(),
                tags,
                prices);
        return product;
    }

    public static record CategoryResponse(Long id, String name, List<ProductResponse> products) {
    }

    public static record EstablishmentInfoResponse(
            Long id,
            String establishmentName,
            String description,
            String instagram,
            String facebook,
            String website,
            String whatsapp,
            String wifiName,
            String wifiPassword,
            String phone,
            String image,
            String addressLine,
            String addressCode,
            String city) {
    }

    public static record ProductResponse(
            Long id,
            String name,
            String image,
            String description,
            boolean newProduct,
            boolean bestSelling,
            String categoryName,
            List<TagsResponse> tags,
            List<PriceResponse> prices) {
    }

    public static record TagsResponse(Long id, String name) {
    }

    public static record PriceResponse(Long id, BigDecimal value, String unit) {
    }
}
