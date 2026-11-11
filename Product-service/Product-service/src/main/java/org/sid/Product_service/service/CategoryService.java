package org.sid.Product_service.service;

import java.util.List;
import org.sid.Product_service.dto.CategoryRequest;
import org.sid.Product_service.dto.CategoryResponse;
import org.sid.Product_service.entity.Category;
import org.sid.Product_service.repository.CategoryRepository;
import org.sid.Product_service.repository.ProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class CategoryService {
    private final CategoryRepository categories;
    private final ProductRepository products;

    public CategoryService(CategoryRepository categories, ProductRepository products) {
        this.categories = categories;
        this.products = products;
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        checkNameAvailability(request.name(), null);
        Category category = new Category();
        apply(category, request);
        return toResponse(categories.save(category));
    }

    public List<CategoryResponse> findAll() {
        return categories.findAll().stream().map(this::toResponse).toList();
    }

    public CategoryResponse findById(Long id) {
        return toResponse(requireCategory(id));
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = requireCategory(id);
        checkNameAvailability(request.name(), id);
        apply(category, request);
        ensureNoCycle(category);
        return toResponse(category);
    }

    @Transactional
    public void delete(Long id) {
        Category category = requireCategory(id);
        if (products.existsByCategoryId(id) || categories.existsByParentId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La catégorie est encore utilisée par un produit ou une sous-catégorie.");
        }
        categories.delete(category);
    }

    Category requireCategory(Long id) {
        return categories.findById(id).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Catégorie introuvable."));
    }

    private void apply(Category category, CategoryRequest request) {
        category.setName(request.name().strip());
        category.setDescription(clean(request.description()));
        category.setParent(request.parentId() == null ? null : requireCategory(request.parentId()));
    }

    private void checkNameAvailability(String name, Long currentId) {
        categories.findByNameIgnoreCase(name.strip())
                .filter(category -> !category.getId().equals(currentId))
                .ifPresent(category -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Une catégorie avec ce nom existe déjà.");
                });
    }

    private void ensureNoCycle(Category category) {
        Category ancestor = category.getParent();
        while (ancestor != null) {
            if (ancestor.getId().equals(category.getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Une catégorie ne peut pas être son propre parent.");
            }
            ancestor = ancestor.getParent();
        }
    }

    CategoryResponse toResponse(Category category) {
        Long parentId = category.getParent() == null ? null : category.getParent().getId();
        return new CategoryResponse(category.getId(), category.getName(), category.getDescription(), parentId);
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
