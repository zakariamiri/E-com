package org.sid.Product_service.repository;

import java.util.Optional;
import org.sid.Product_service.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    Optional<Category> findByNameIgnoreCase(String name);

    boolean existsByParentId(Long parentId);
}
