package com.autoparts_maven.autoparts_maven.cart.dto;

import com.autoparts_maven.autoparts_maven.product.dto.ProductResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public final class CartDtos {

    private CartDtos() {
    }

    /** Sets the quantity of a product in the cart (0 removes it). */
    public record SetQuantityRequest(@NotNull @Min(0) @Max(99) Integer quantity) {
    }

    /**
     * {@code available} is false when the product was deactivated or the stock no longer covers
     * the quantity; checkout refuses the cart until the customer fixes those lines.
     */
    public record CartLine(ProductResponse product, int quantity, BigDecimal lineTotal, boolean available) {
    }

    /**
     * The delivery fee depends on the address: {@code deliveryRegion}, {@code deliveryFee} and
     * {@code total} are filled only when the cart is read with the {@code addressId} of an address
     * whose district is served, and are null otherwise. {@code readyForCheckout} is false when the
     * cart is empty, a line is unavailable or the given address is not served.
     */
    public record CartResponse(List<CartLine> items, int itemCount, BigDecimal subtotal, String deliveryRegion,
            BigDecimal deliveryFee, BigDecimal total, boolean readyForCheckout) {
    }
}
