package com.autoparts_maven.autoparts_maven.cart;

import com.autoparts_maven.autoparts_maven.cart.dto.CartDtos.CartLine;
import com.autoparts_maven.autoparts_maven.cart.dto.CartDtos.CartResponse;
import com.autoparts_maven.autoparts_maven.product.Product;
import com.autoparts_maven.autoparts_maven.product.ProductNotFoundException;
import com.autoparts_maven.autoparts_maven.product.ProductRepository;
import com.autoparts_maven.autoparts_maven.product.dto.ProductResponse;
import com.autoparts_maven.autoparts_maven.customer.AddressService;
import com.autoparts_maven.autoparts_maven.delivery.DeliveryDtos.DeliveryQuote;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The signed-in customer's cart. It holds products and quantities only; prices, availability and
 * totals are always computed from the current catalog and delivery regions. The authoritative stock check
 * happens at checkout, under a row lock.
 */
@Service
@RequiredArgsConstructor
public class CartService {

    static final int MAX_LINES = 50;

    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final AddressService addressService;

    @Transactional(readOnly = true)
    public CartResponse get(Long userId) {
        return get(userId, null);
    }

    /** With an {@code addressId}, also prices the delivery to that address (404 if not the user's). */
    @Transactional(readOnly = true)
    public CartResponse get(Long userId, Long addressId) {
        List<CartItem> items = cartItemRepository.findByUserIdOrderByIdAsc(userId);
        List<CartLine> lines = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        int count = 0;
        boolean allAvailable = true;
        for (CartItem item : items) {
            Product product = item.getProduct();
            BigDecimal lineTotal = product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            boolean available = isAvailable(product, item.getQuantity());
            allAvailable &= available;
            lines.add(new CartLine(ProductResponse.from(product), item.getQuantity(), lineTotal, available));
            subtotal = subtotal.add(lineTotal);
            count += item.getQuantity();
        }
        Optional<DeliveryQuote> quote = addressId == null ? Optional.empty() : addressService.quote(userId, addressId);
        boolean ready = !items.isEmpty() && allAvailable && (addressId == null || quote.isPresent());
        BigDecimal fee = quote.map(DeliveryQuote::fee).orElse(null);
        return new CartResponse(lines, count, subtotal, quote.map(DeliveryQuote::region).orElse(null), fee,
                fee == null ? null : subtotal.add(fee), ready);
    }

    /** Sets the quantity of a product (0 removes it). Rejects inactive products and quantities above stock. */
    @Transactional
    public CartResponse setQuantity(Long userId, Long productId, int quantity) {
        if (quantity == 0) {
            cartItemRepository.deleteItem(userId, productId);
            return get(userId);
        }
        Product product = productRepository.findById(productId)
                .filter(p -> Boolean.TRUE.equals(p.getActive()))
                .orElseThrow(() -> new ProductNotFoundException(productId));
        if (product.getStockQuantity() < quantity) {
            throw new InsufficientStockException(product.getSku(), product.getStockQuantity());
        }
        CartItem item = cartItemRepository.findByUserIdAndProductId(userId, productId).orElseGet(() -> {
            if (cartItemRepository.countByUserId(userId) >= MAX_LINES) {
                throw new CartFullException(MAX_LINES);
            }
            CartItem created = new CartItem();
            created.setUserId(userId);
            created.setProduct(product);
            return created;
        });
        item.setQuantity(quantity);
        cartItemRepository.saveAndFlush(item);
        return get(userId);
    }

    @Transactional
    public CartResponse remove(Long userId, Long productId) {
        cartItemRepository.deleteItem(userId, productId);
        return get(userId);
    }

    @Transactional
    public void clear(Long userId) {
        cartItemRepository.deleteAllForUser(userId);
    }

    /** Lines for checkout, read in the caller's transaction. */
    @Transactional(readOnly = true)
    public List<CartItem> items(Long userId) {
        return cartItemRepository.findByUserIdOrderByIdAsc(userId);
    }

    static boolean isAvailable(Product product, int quantity) {
        return Boolean.TRUE.equals(product.getActive()) && product.getStockQuantity() >= quantity;
    }
}
