package org.sid.Product_service.service;

import java.util.List;
import java.util.Locale;
import org.sid.Product_service.dto.ProductRequest;
import org.sid.Product_service.dto.ProductResponse;
import org.sid.Product_service.entity.Product;
import org.sid.Product_service.entity.ProductStatus;
import org.sid.Product_service.repository.ProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class ProductService {
    private final ProductRepository products;
    private final CategoryService categories;

    public ProductService(ProductRepository products, CategoryService categories) {
        this.products = products;
        this.categories = categories;
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        String sku = normalizeSku(request.sku());
        checkSkuAvailability(sku, null);
        Product product = new Product();
        apply(product, request, sku);
        return toResponse(products.save(product));
    }

    public List<ProductResponse> findAll(String search) {
        List<Product> result = search == null || search.isBlank()
                ? products.findAll()
                : products.search(search.strip());
        return result.stream().map(this::toResponse).toList();
    }

    public ProductResponse findById(Long id) {
        return toResponse(requireProduct(id));
    }

    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = requireProduct(id);
        String sku = normalizeSku(request.sku());
        checkSkuAvailability(sku, id);
        apply(product, request, sku);
        return toResponse(product);
    }

    @Transactional
    public ProductResponse reserveStock(Long id, int quantity) {
        Product product = requireProductForUpdate(id);
        if (product.getStatus() != ProductStatus.PUBLISHED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Le produit n'est pas publié.");
        }
        if (product.getStockQuantity() < quantity) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Stock insuffisant.");
        }
        product.setStockQuantity(product.getStockQuantity() - quantity);
        return toResponse(product);
    }

    @Transactional
    public ProductResponse releaseStock(Long id, int quantity) {
        Product product = requireProductForUpdate(id);
        product.setStockQuantity(product.getStockQuantity() + quantity);
        return toResponse(product);
    }

    @Transactional
    public void delete(Long id) {
        products.delete(requireProduct(id));
    }

    private Product requireProduct(Long id) {
        return products.findById(id).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Produit introuvable."));
    }

    private Product requireProductForUpdate(Long id) {
        return products.findByIdForUpdate(id).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Produit introuvable."));
    }

    private void apply(Product product, ProductRequest request, String sku) {
        product.setName(request.name().strip());
        product.setDescription(clean(request.description()));
        product.setBrand(request.brand().strip());
        product.setSku(sku);
        product.setPrice(request.price());
        product.setStockQuantity(request.stockQuantity());
        product.setStatus(request.status());
        product.setCategory(categories.requireCategory(request.categoryId()));
    }

    private void checkSkuAvailability(String sku, Long currentId) {
        products.findBySkuIgnoreCase(sku)
                .filter(product -> !product.getId().equals(currentId))
                .ifPresent(product -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce SKU existe déjà.");
                });
    }

    private ProductResponse toResponse(Product product) {
        boolean available = product.getStatus() == ProductStatus.PUBLISHED && product.getStockQuantity() > 0;
        return new ProductResponse(product.getId(), product.getName(), product.getDescription(),
                product.getBrand(), product.getSku(), product.getPrice(), product.getStockQuantity(),
                product.getStatus(), available, categories.toResponse(product.getCategory()),
                product.getCreatedAt(), product.getUpdatedAt());
    }

    private String normalizeSku(String sku) {
        return sku.strip().toUpperCase(Locale.ROOT);
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
