package com.menuonline.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.menuonline.controller.ProductController.ProductFilterParamRequest;
import com.menuonline.entity.Category;
import com.menuonline.entity.Price;
import com.menuonline.entity.Product;
import com.menuonline.entity.Tags;
import com.menuonline.entity.UserEntity;
import com.menuonline.exceptions.ErrorHandlerResponse.ErrorMessages;
import com.menuonline.exceptions.HttpServiceException;
import com.menuonline.payloads.CreateProductRequest;
import com.menuonline.payloads.ProductResponse;
import com.menuonline.repository.CategoryRepository;
import com.menuonline.repository.PriceRepository;
import com.menuonline.repository.ProductRepository;
import com.menuonline.repository.TagsRepository;
import com.menuonline.spec.ProductSpecification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final PriceRepository priceRepository;
    private final TagsRepository tagsRepository;

    public Page<ProductResponse> getAll(UserEntity user, int page, int size,
            ProductFilterParamRequest filter) {
        log.info("get all products - user:{} page:{} size:{}", user.getId(), page, size);

        List<Specification<Product>> specs = new ArrayList<>();
        if (filter.categoryId() != null) {
            Category category = categoryRepository
                    .findByUserIdAndId(user.getId(), filter.categoryId())
                    .orElseThrow(() -> new HttpServiceException(ErrorMessages.PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND));
            specs.add(ProductSpecification.isCategory(category));
        } else {
            specs.add(
                    ProductSpecification.inCategoryList(categoryRepository.findByUserIdOrderBySequence(user.getId())));
        }
        if (filter.name() != null) {
            specs.add(ProductSpecification.byName(filter.name()));
        }
        if (filter.active() != null) {
            specs.add(ProductSpecification.byStatus(filter.active()));
        }
        return productRepository.findAll(Specification.allOf(specs), PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id")))
                .map(ProductResponse::from);
    }

    @Transactional
    public Product create(CreateProductRequest request) {
        Category category = categoryRepository
                .findById(request.categoryId())
                .orElseThrow(() -> new HttpServiceException(null, HttpStatus.CONFLICT));

        Product product = new Product();
        product.setName(request.name());
        product.setDescription(request.description());
        product.setCategory(category);
        product.setActive(request.active());
        product.setBestSelling(request.bestSelling());
        product.setNewProduct(request.newProduct());
        product.setTags(loadProductTags(request));
        Product saved = productRepository.save(product);
        log.info("create product - id:{}", saved.getId());

        savePrices(request, product);

        return saved;
    }

    private List<Tags> loadProductTags(CreateProductRequest request) {
        if (request.tagsIds() == null || request.tagsIds().isEmpty()) {
            return List.of();
        }
        List<Tags> allById = tagsRepository.findAllById(request.tagsIds());
        return allById;
    }

    private List<Price> savePrices(CreateProductRequest request, Product product) {
        log.info("savePrices - product:{}", product);
        List<Price> prices = request.prices()
                .stream()
                .filter(p -> p.value() != null)
                .map(p -> new Price(p.id(), p.value(), p.unit(), product))
                .toList();
        log.info("savePrices - prices:{}" + prices);
        return priceRepository.saveAll(prices);
    }

    public Optional<Product> getById(Long id, Long userId) {
        return productRepository.findByIdAndUserId(id, userId);
    }

    public void updateProduct(Product product, CreateProductRequest request) {
        Category category = categoryRepository
                .findById(request.categoryId())
                .orElseThrow(() -> new HttpServiceException(null, HttpStatus.CONFLICT));
        product.setName(request.name());
        product.setActive(request.active());
        product.setDescription(request.description());
        product.setCategory(category);
        product.setBestSelling(request.bestSelling());
        product.setNewProduct(request.newProduct());
        List<Price> newPrices = new ArrayList<>();
        for (var rp : request.prices()) {
            newPrices.add(new Price(null, rp.value(), rp.unit(), product));
        }
        priceRepository.deleteAll(product.getPrices());
        product.setPrices(newPrices);
        product.setTags(loadProductTags(request));
    }

    public void toggleActive(Long id, UserEntity user) {
        log.info("toggleActive - id:{}", id);
        Product product = productRepository
                .findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new HttpServiceException(ErrorMessages.PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND));
        productRepository.toggleActive(product.getId());
    }

    public void delete(Product product) {
        log.info("delete - productId:{}", product.getId());
        productRepository.delete(product);
    }

    public Optional<Product> findByUserAndId(Long userId, Long productId) {
        return productRepository.findByIdAndUserId(productId, userId);
    }
}
