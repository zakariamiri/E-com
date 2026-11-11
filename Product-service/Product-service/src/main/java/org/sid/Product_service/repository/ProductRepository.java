package org.sid.Product_service.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.sid.Product_service.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findBySkuIgnoreCase(String sku);

    boolean existsByCategoryId(Long categoryId);

    @Query("""
            select product from Product product
            where lower(product.name) like lower(concat('%', :term, '%'))
               or lower(product.brand) like lower(concat('%', :term, '%'))
               or lower(product.sku) like lower(concat('%', :term, '%'))
            order by product.name
            """)
    List<Product> search(@Param("term") String term);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select product from Product product where product.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);
}
