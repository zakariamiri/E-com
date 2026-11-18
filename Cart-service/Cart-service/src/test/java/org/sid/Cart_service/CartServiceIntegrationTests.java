package org.sid.Cart_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.sid.Cart_service.dto.AddToCartRequest;
import org.sid.Cart_service.dto.CartResponse;
import org.sid.Cart_service.dto.CreateCartRequest;
import org.sid.Cart_service.dto.UpdateQuantityRequest;
import org.sid.Cart_service.services.CartService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@Transactional
class CartServiceIntegrationTests {

    @Autowired
    private CartService cartService;

    @Test
    void createsCartAndFindsIt() {
        CartResponse created = cartService.createCart(new CreateCartRequest(101L));

        assertNotNull(created.id());
        assertEquals(101L, created.userId());
        assertEquals(0, created.totalItemsCount());
        assertTrue(created.items().isEmpty());

        CartResponse byId = cartService.getCartById(created.id());
        assertEquals(created.id(), byId.id());

        CartResponse byUserId = cartService.getCartByUserId(101L);
        assertEquals(created.id(), byUserId.id());
    }

    @Test
    void rejectsDuplicateCartForSameUser() {
        cartService.createCart(new CreateCartRequest(102L));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> cartService.createCart(new CreateCartRequest(102L)));

        assertEquals(409, exception.getStatusCode().value());
    }

    @Test
    void addsItemsAndCalculatesTotalCount() {
        CartResponse cart = cartService.createCart(new CreateCartRequest(103L));

        CartResponse afterFirst = cartService.addItem(cart.id(), new AddToCartRequest(10L, 2));
        assertEquals(1, afterFirst.items().size());
        assertEquals(2, afterFirst.totalItemsCount());
        assertEquals(10L, afterFirst.items().get(0).productId());
        assertEquals(2, afterFirst.items().get(0).quantity());

        // Adding same product again should increment quantity
        CartResponse afterDuplicate = cartService.addItem(cart.id(), new AddToCartRequest(10L, 3));
        assertEquals(1, afterDuplicate.items().size());
        assertEquals(5, afterDuplicate.totalItemsCount());
        assertEquals(5, afterDuplicate.items().get(0).quantity());

        // Adding a second product
        CartResponse afterSecond = cartService.addItem(cart.id(), new AddToCartRequest(20L, 1));
        assertEquals(2, afterSecond.items().size());
        assertEquals(6, afterSecond.totalItemsCount());
    }

    @Test
    void updatesItemQuantity() {
        CartResponse cart = cartService.createCart(new CreateCartRequest(104L));
        cartService.addItem(cart.id(), new AddToCartRequest(10L, 2));

        CartResponse updated = cartService.updateItemQuantity(cart.id(), 10L, new UpdateQuantityRequest(7));
        assertEquals(7, updated.items().get(0).quantity());
        assertEquals(7, updated.totalItemsCount());
    }

    @Test
    void removesItemAndClearsCart() {
        CartResponse cart = cartService.createCart(new CreateCartRequest(105L));
        cartService.addItem(cart.id(), new AddToCartRequest(10L, 2));
        cartService.addItem(cart.id(), new AddToCartRequest(20L, 1));

        CartResponse afterRemove = cartService.removeItem(cart.id(), 10L);
        assertEquals(1, afterRemove.items().size());
        assertEquals(20L, afterRemove.items().get(0).productId());
        assertEquals(1, afterRemove.totalItemsCount());

        CartResponse afterClear = cartService.clearCart(cart.id());
        assertEquals(0, afterClear.items().size());
        assertEquals(0, afterClear.totalItemsCount());
    }
}
