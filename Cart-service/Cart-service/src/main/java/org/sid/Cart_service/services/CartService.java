package org.sid.Cart_service.services;

import org.sid.Cart_service.dto.AddToCartRequest;
import org.sid.Cart_service.dto.CartItemResponse;
import org.sid.Cart_service.dto.CartResponse;
import org.sid.Cart_service.dto.CreateCartRequest;
import org.sid.Cart_service.dto.UpdateQuantityRequest;
import org.sid.Cart_service.entities.Cart;
import org.sid.Cart_service.entities.CartItem;
import org.sid.Cart_service.repositories.CartRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@Transactional
public class CartService {

    private final CartRepository cartRepository;

    public CartService(CartRepository cartRepository) {
        this.cartRepository = cartRepository;
    }

    public CartResponse createCart(CreateCartRequest request) {
        if (request.userId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId must not be null");
        }

        if (cartRepository.existsByUserId(request.userId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cart already exists for user: " + request.userId());
        }

        Cart cart = new Cart(request.userId());
        Cart saved = cartRepository.save(cart);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public CartResponse getCartById(Long id) {
        Cart cart = cartRepository.findByIdWithItems(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cart not found with id: " + id));
        return mapToResponse(cart);
    }

    @Transactional(readOnly = true)
    public CartResponse getCartByUserId(Long userId) {
        Cart cart = cartRepository.findByUserIdWithItems(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cart not found for user: " + userId));
        return mapToResponse(cart);
    }

    public CartResponse getOrCreateCartByUserId(Long userId) {
        return cartRepository.findByUserIdWithItems(userId)
                .map(this::mapToResponse)
                .orElseGet(() -> createCart(new CreateCartRequest(userId)));
    }

    @Transactional(readOnly = true)
    public List<CartResponse> getAllCarts() {
        return cartRepository.findAll().stream()
                .map(this::mapToResponse)
                .toList();
    }

    public CartResponse addItem(Long cartId, AddToCartRequest request) {
        Cart cart = cartRepository.findByIdWithItems(cartId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cart not found with id: " + cartId));

        cart.addItem(request.productId(), request.quantity());
        Cart saved = cartRepository.save(cart);
        return mapToResponse(saved);
    }

    public CartResponse updateItemQuantity(Long cartId, Long productId, UpdateQuantityRequest request) {
        Cart cart = cartRepository.findByIdWithItems(cartId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cart not found with id: " + cartId));

        boolean exists = cart.getItems().stream().anyMatch(item -> item.getProductId().equals(productId));
        if (!exists) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product " + productId + " not found in cart " + cartId);
        }

        cart.updateItemQuantity(productId, request.quantity());
        Cart saved = cartRepository.save(cart);
        return mapToResponse(saved);
    }

    public CartResponse removeItem(Long cartId, Long productId) {
        Cart cart = cartRepository.findByIdWithItems(cartId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cart not found with id: " + cartId));

        boolean removed = cart.removeItem(productId);
        if (!removed) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product " + productId + " not found in cart " + cartId);
        }

        Cart saved = cartRepository.save(cart);
        return mapToResponse(saved);
    }

    public CartResponse clearCart(Long cartId) {
        Cart cart = cartRepository.findByIdWithItems(cartId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cart not found with id: " + cartId));

        cart.clearItems();
        Cart saved = cartRepository.save(cart);
        return mapToResponse(saved);
    }

    public void deleteCart(Long cartId) {
        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cart not found with id: " + cartId));
        cartRepository.delete(cart);
    }

    private CartResponse mapToResponse(Cart cart) {
        List<CartItemResponse> itemResponses = cart.getItems().stream()
                .map(item -> new CartItemResponse(item.getId(), item.getProductId(), item.getQuantity()))
                .toList();

        int totalCount = cart.getItems().stream()
                .mapToInt(CartItem::getQuantity)
                .sum();

        return new CartResponse(
                cart.getId(),
                cart.getUserId(),
                cart.getUpdatedAt(),
                itemResponses,
                totalCount
        );
    }
}
