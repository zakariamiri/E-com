package org.sid.Product_service.controller;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.sid.Product_service.dto.ProductRequest;
import org.sid.Product_service.dto.ProductResponse;
import org.sid.Product_service.dto.StockRequest;
import org.sid.Product_service.service.ProductService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    private final ProductService products;

    public ProductController(ProductService products) {
        this.products = products;
    }

    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        ProductResponse product = products.create(request);
        return ResponseEntity.created(URI.create("/api/products/" + product.id())).body(product);
    }

    @GetMapping
    public List<ProductResponse> findAll(@RequestParam(required = false) String search) {
        return products.findAll(search);
    }

    @GetMapping("/{id}")
    public ProductResponse findById(@PathVariable Long id) {
        return products.findById(id);
    }

    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return products.update(id, request);
    }

    @PatchMapping("/{id}/stock/reserve")
    public ProductResponse reserveStock(@PathVariable Long id, @Valid @RequestBody StockRequest request) {
        return products.reserveStock(id, request.quantity());
    }

    @PatchMapping("/{id}/stock/release")
    public ProductResponse releaseStock(@PathVariable Long id, @Valid @RequestBody StockRequest request) {
        return products.releaseStock(id, request.quantity());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        products.delete(id);
    }
}
