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

    /** {@code deliveryFee} and {@code total} are null while the store has not configured the fee. */
    public record CartResponse(List<CartLine> items, int itemCount, BigDecimal subtotal, BigDecimal deliveryFee,
            BigDecimal total, boolean readyForCheckout) {
    }
}
