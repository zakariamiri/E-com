package org.sid.Order_service.services;

import org.sid.Order_service.dto.ProductInfo;
import org.sid.Order_service.dto.StockRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "product-service", path = "/api/products")
public interface ProductClient {
    @GetMapping("/{id}")
    ProductInfo getProduct(@PathVariable("id") Long productId);

    @PatchMapping("/{id}/stock/reserve")
    ProductInfo reserve(@PathVariable("id") Long productId, @RequestBody StockRequest request);

    @PatchMapping("/{id}/stock/release")
    ProductInfo release(@PathVariable("id") Long productId, @RequestBody StockRequest request);
}
