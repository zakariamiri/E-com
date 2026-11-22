package org.sid.Order_service.services;

import org.sid.Order_service.dto.CartResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "cart-service", path = "/api/carts")
public interface CartClient {
    @GetMapping("/user/{userId}")
    CartResponse getCartByUserId(@PathVariable("userId") Long userId);

    @DeleteMapping("/{id}/items")
    CartResponse clearCart(@PathVariable("id") Long cartId);
}
