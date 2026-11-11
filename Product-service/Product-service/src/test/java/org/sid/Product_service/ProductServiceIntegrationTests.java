package org.sid.Product_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.sid.Product_service.dto.CategoryRequest;
import org.sid.Product_service.dto.CategoryResponse;
import org.sid.Product_service.dto.ProductRequest;
import org.sid.Product_service.dto.ProductResponse;
import org.sid.Product_service.entity.ProductStatus;
import org.sid.Product_service.service.CategoryService;
import org.sid.Product_service.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@Transactional
class ProductServiceIntegrationTests {
    @Autowired
    private CategoryService categories;

    @Autowired
    private ProductService products;

    @Test
    void createsSearchesAndReservesAProduct() {
        CategoryResponse category = categories.create(new CategoryRequest("Ordinateurs", null, null));
        ProductResponse created = products.create(new ProductRequest(
                "Laptop Pro", "Ordinateur de démonstration", "DemoBrand", "lap-001",
                new BigDecimal("899.90"), 5, ProductStatus.PUBLISHED, category.id()));

        assertEquals("LAP-001", created.sku());
        assertTrue(created.available());
        assertEquals(1, products.findAll("laptop").size());

        ProductResponse reserved = products.reserveStock(created.id(), 2);
        assertEquals(3, reserved.stockQuantity());
    }

    @Test
    void rejectsAReservationWhenStockIsInsufficient() {
        CategoryResponse category = categories.create(new CategoryRequest("Téléphones", null, null));
        ProductResponse created = products.create(new ProductRequest(
                "Téléphone Demo", null, "DemoBrand", "tel-001",
                new BigDecimal("299.00"), 1, ProductStatus.PUBLISHED, category.id()));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> products.reserveStock(created.id(), 2));

        assertEquals(409, exception.getStatusCode().value());
        assertEquals(1, products.findById(created.id()).stockQuantity());
    }
}
