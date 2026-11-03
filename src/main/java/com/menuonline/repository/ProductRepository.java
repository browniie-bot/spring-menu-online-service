package com.menuonline.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.menuonline.entity.Product;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = """
                update products p
                set active = not p.active, updated_at = now()
                where id = :id
            """)
    public void toggleActive(long id);

    @Query(nativeQuery = true, value = """
                select p.*
                from products p
                join categories c on (p.category_id = c.id)
                join users u on (c.user_id = u.id)
                where u.id = :userId and p.id = :id
            """)
    public Optional<Product> findByIdAndUserId(long id, long userId);

    @Query(nativeQuery = true, value = """
            select
                p.id,
                p.name as productName,
                p.description,
                p.image,
                p.active,
                p.created_at,
                p.updated_at,
                p.category_id,
                c.name as categoryName
            from products p
            join categories c on (c.id = p.category_id)
            join users u on (u.id = c.user_id)
            where u.id = :userId
            order by c.name, productName desc
            """)
    Page<ProductProjection> findByUserId(long userId, Pageable pageable);

    List<Product> findByCategoryIdIn(List<Long> categoriesIds);

    List<Product> findProductByTags_Id(Long id);

    public static interface ProductProjection {
        public long getId();

        public String getProductName();

        public String getDescription();

        public boolean isActive();

        public String getImage();

        public LocalDateTime getCreatedAt();

        public LocalDateTime getUpdatedAt();

        public long getCategoryId();

        public String getCategoryName();
    }

    List<Product> findByCategoryId(Long id);


    @Query(nativeQuery = true, value = """
            SELECT
                p.id,
                p.name AS productName,
                p.image,
                p.active,
                p.category_id,
                p.description,
                p.new_product,
                p.best_selling,
                pr.id AS priceId,
                pr.unit,
                pr.value,
                c.name AS categoryName,
                t.id AS tagId,
                t.name AS tagName
            FROM products p
            JOIN prices pr ON (p.id = pr.product_id)
            JOIN categories c ON (c.id = p.category_id)
            JOIN users u ON (u.id = c.user_id)
            LEFT JOIN product_tags pt ON (pt.product_id = p.id)
            LEFT JOIN tags t ON (t.id = pt.tag_id)
            WHERE u.id = :userId
            AND c.enabled IS true
            AND p.active IS true
            ORDER BY c.sequence, p.name, pr.value;
            """)
    List<ProductMenuProjection> findMenu(long userId);

    public static interface ProductMenuProjection {
        public long getId();
        public String getProductName();
        public String getImage();
        public boolean isActive();
        public long getCategoryId();
        public String getDescription();
        public boolean isNewProduct();
        public boolean isBestSelling();
        public Long getPriceId();
        public String getUnit();
        public BigDecimal getValue();
        public String getCategoryName();
        public Long getTagId();
        public String getTagName();
    }

}
