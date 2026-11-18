package org.sid.Cart_service.controller;

import jakarta.validation.Valid;
import org.sid.Cart_service.dto.AddToCartRequest;
import org.sid.Cart_service.dto.CartResponse;
import org.sid.Cart_service.dto.CreateCartRequest;
import org.sid.Cart_service.dto.UpdateQuantityRequest;
import org.sid.Cart_service.services.CartService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/carts")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @PostMapping
    public ResponseEntity<CartResponse> createCart(@Valid @RequestBody CreateCartRequest request) {
        CartResponse created = cartService.createCart(request);
        return ResponseEntity.created(URI.create("/api/carts/" + created.id())).body(created);
    }

    @GetMapping
    public List<CartResponse> getAllCarts() {
        return cartService.getAllCarts();
    }

    @GetMapping("/{id}")
    public CartResponse getCartById(@PathVariable("id") Long id) {
        return cartService.getCartById(id);
    }

    @GetMapping("/user/{userId}")
    public CartResponse getCartByUserId(@PathVariable("userId") Long userId) {
        return cartService.getCartByUserId(userId);
    }

    @PostMapping("/{id}/items")
    public CartResponse addItem(@PathVariable("id") Long id, @Valid @RequestBody AddToCartRequest request) {
        return cartService.addItem(id, request);
    }

    @PutMapping("/{id}/items/{productId}")
    public CartResponse updateItemQuantity(
            @PathVariable("id") Long id,
            @PathVariable("productId") Long productId,
            @Valid @RequestBody UpdateQuantityRequest request) {
        return cartService.updateItemQuantity(id, productId, request);
    }

    @DeleteMapping("/{id}/items/{productId}")
    public CartResponse removeItem(
            @PathVariable("id") Long id,
            @PathVariable("productId") Long productId) {
        return cartService.removeItem(id, productId);
    }

    @DeleteMapping("/{id}/items")
    public CartResponse clearCart(@PathVariable("id") Long id) {
        return cartService.clearCart(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCart(@PathVariable("id") Long id) {
        cartService.deleteCart(id);
    }
}
