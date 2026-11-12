package com.autoparts_maven.autoparts_maven.order;

import com.autoparts_maven.autoparts_maven.cart.CartItem;
import com.autoparts_maven.autoparts_maven.cart.CartService;
import com.autoparts_maven.autoparts_maven.cart.InsufficientStockException;
import com.autoparts_maven.autoparts_maven.common.PageResponse;
import com.autoparts_maven.autoparts_maven.customer.Address;
import com.autoparts_maven.autoparts_maven.customer.AddressService;
import com.autoparts_maven.autoparts_maven.customer.AddressService.DeliverableAddress;
import com.autoparts_maven.autoparts_maven.order.dto.OrderDtos.OrderResponse;
import com.autoparts_maven.autoparts_maven.order.dto.OrderDtos.OrderSummary;
import com.autoparts_maven.autoparts_maven.product.Product;
import com.autoparts_maven.autoparts_maven.product.ProductRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    static final String EXPIRED_REASON = "Pagamento nao confirmado no prazo";
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final CartService cartService;
    private final AddressService addressService;
    private final ProductRepository productRepository;
    private final Duration paymentTimeout;

    public OrderService(OrderRepository orderRepository, CartService cartService, AddressService addressService,
            ProductRepository productRepository,
            @Value("${orders.payment-timeout:30m}") Duration paymentTimeout) {
        this.orderRepository = orderRepository;
        this.cartService = cartService;
        this.addressService = addressService;
        this.productRepository = productRepository;
        this.paymentTimeout = paymentTimeout;
    }

    /**
     * Turns the cart into an order awaiting payment.
     *
     * <p>Products are locked (in id order, so concurrent checkouts cannot deadlock), checked for
     * availability and their stock is decremented, i.e. reserved until payment or expiry. Prices,
     * names, the address and the delivery fee of its region are copied into the order. Repeating the call with the same
     * Idempotency-Key returns the order already created instead of creating a second one.
     */
    @Transactional
    public OrderResponse checkout(Long userId, Long addressId, String idempotencyKey) {
        if (idempotencyKey == null || !IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
            throw new InvalidIdempotencyKeyException();
        }
        var existing = orderRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (existing.isPresent()) {
            return OrderResponse.from(existing.get());
        }

        DeliverableAddress deliverable = addressService.getDeliverable(userId, addressId);
        Address address = deliverable.address();
        BigDecimal deliveryFee = deliverable.quote().fee();
        List<CartItem> cart = cartService.items(userId);
        if (cart.isEmpty()) {
            throw new EmptyCartException();
        }

        List<Long> productIds = cart.stream().map(item -> item.getProduct().getId()).sorted().toList();
        Map<Long, Product> locked = productRepository.findAllForUpdate(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        Order order = new Order();
        order.setUserId(userId);
        order.setStatus(OrderStatus.AWAITING_PAYMENT);
        copyAddress(address, order);
        order.setIdempotencyKey(idempotencyKey);
        order.setPaymentDueAt(Instant.now().plus(paymentTimeout));

        BigDecimal subtotal = BigDecimal.ZERO;
        for (CartItem line : cart) {
            Product product = locked.get(line.getProduct().getId());
            if (product == null || !Boolean.TRUE.equals(product.getActive())) {
                throw new ProductUnavailableException(line.getProduct().getSku());
            }
            if (product.getStockQuantity() < line.getQuantity()) {
                throw new InsufficientStockException(product.getSku(), product.getStockQuantity());
            }
            product.setStockQuantity(product.getStockQuantity() - line.getQuantity());

            OrderItem item = new OrderItem();
            item.setProductId(product.getId());
            item.setSku(product.getSku());
            item.setProductName(product.getName());
            item.setUnitPrice(product.getPrice());
            item.setQuantity(line.getQuantity());
            item.setLineTotal(product.getPrice().multiply(BigDecimal.valueOf(line.getQuantity())));
            order.addItem(item);
            subtotal = subtotal.add(item.getLineTotal());
        }
        order.setSubtotal(subtotal);
        order.setDeliveryFee(deliveryFee);
        order.setDeliveryRegion(deliverable.quote().region());
        order.setTotal(subtotal.add(deliveryFee));

        Order saved = orderRepository.saveAndFlush(order);
        cartService.clear(userId);
        return OrderResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderSummary> listMine(Long userId, Pageable pageable) {
        return PageResponse.from(orderRepository.findByUserId(userId, pageable).map(OrderSummary::from));
    }

    @Transactional(readOnly = true)
    public OrderResponse getMine(Long userId, Long orderId) {
        return OrderResponse.from(orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new OrderNotFoundException(orderId)));
    }

    /** The customer may cancel only while the order awaits payment; reserved stock is returned. */
    @Transactional
    public OrderResponse cancelMine(Long userId, Long orderId, String reason) {
        orderRepository.findByIdAndUserId(orderId, userId).orElseThrow(() -> new OrderNotFoundException(orderId));
        Order order = orderRepository.findForUpdate(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
        if (order.getStatus() != OrderStatus.AWAITING_PAYMENT) {
            throw new InvalidOrderTransitionException(orderId, order.getStatus(), OrderStatus.CANCELLED);
        }
        cancel(order, reason == null || reason.isBlank() ? "Cancelado pelo cliente" : reason.trim());
        return OrderResponse.from(order);
    }

    /** What the payment module needs to know about an order before charging it. */
    public record PayableOrder(Long id, Long userId, OrderStatus status, BigDecimal total, Instant paymentDueAt) {
    }

    /** The customer's own order, locked so concurrent payment attempts are serialized. */
    @Transactional
    public PayableOrder lockForPayment(Long userId, Long orderId) {
        orderRepository.findByIdAndUserId(orderId, userId).orElseThrow(() -> new OrderNotFoundException(orderId));
        Order order = orderRepository.findForUpdate(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
        return new PayableOrder(order.getId(), order.getUserId(), order.getStatus(), order.getTotal(), order.getPaymentDueAt());
    }

    @Transactional
    public PayableOrder lockForPayment(Long orderId) {
        Order order = orderRepository.findForUpdate(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
        return new PayableOrder(order.getId(), order.getUserId(), order.getStatus(), order.getTotal(), order.getPaymentDueAt());
    }

    /**
     * Keeps an order alive while a payment that can still be completed is open (e.g. a Pix that
     * expires later than the order would): the deadline only ever moves forward.
     */
    @Transactional
    public void extendPaymentDue(Long orderId, Instant atLeast) {
        Order order = orderRepository.findForUpdate(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
        if (order.getStatus() == OrderStatus.AWAITING_PAYMENT && order.getPaymentDueAt().isBefore(atLeast)) {
            order.setPaymentDueAt(atLeast);
        }
    }

    /** Marks the order paid if it still awaits payment; returns false when it no longer does. */
    @Transactional
    public boolean markPaid(Long orderId) {
        Order order = orderRepository.findForUpdate(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
        if (order.getStatus() != OrderStatus.AWAITING_PAYMENT) {
            return false;
        }
        order.moveTo(OrderStatus.PAID);
        return true;
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderSummary> search(OrderStatus status, Pageable pageable) {
        return PageResponse.from(orderRepository.search(status, pageable).map(OrderSummary::from));
    }

    @Transactional(readOnly = true)
    public OrderResponse get(Long orderId) {
        return OrderResponse.from(orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId)));
    }

    /**
     * Cancels orders whose payment deadline passed and returns their stock. Each order is locked
     * and re-checked, so an order paid at the last moment is never cancelled.
     */
    @Transactional
    public int expireOverdue(Instant now, int batchSize) {
        List<Long> ids = orderRepository.findIdsDueBefore(OrderStatus.AWAITING_PAYMENT, now, PageRequest.of(0, batchSize));
        int expired = 0;
        for (Long id : ids) {
            Order order = orderRepository.findForUpdate(id).orElse(null);
            if (order != null && order.getStatus() == OrderStatus.AWAITING_PAYMENT && order.getPaymentDueAt().isBefore(now)) {
                cancel(order, EXPIRED_REASON);
                expired++;
            }
        }
        if (expired > 0) {
            log.info("Expired {} unpaid orders", expired);
        }
        return expired;
    }

    private void cancel(Order order, String reason) {
        order.moveTo(OrderStatus.CANCELLED);
        order.setCancelledAt(Instant.now());
        order.setCancelReason(reason.length() > 200 ? reason.substring(0, 200) : reason);
        returnStock(order);
    }

    private void returnStock(Order order) {
        List<Long> productIds = order.getItems().stream().map(OrderItem::getProductId)
                .filter(Objects::nonNull).distinct().sorted().toList();
        if (productIds.isEmpty()) {
            return;
        }
        Map<Long, Product> locked = productRepository.findAllForUpdate(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        for (OrderItem item : order.getItems()) {
            Product product = item.getProductId() == null ? null : locked.get(item.getProductId());
            if (product != null) {
                product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
            }
        }
    }

    private static void copyAddress(Address address, Order order) {
        order.setRecipientName(address.getRecipientName());
        order.setPhone(address.getPhone());
        order.setCep(address.getCep());
        order.setStreet(address.getStreet());
        order.setNumber(address.getNumber());
        order.setComplement(address.getComplement());
        order.setDistrict(address.getDistrict());
        order.setCity(address.getCity());
        order.setState(address.getState());
        order.setReference(address.getReference());
    }
}
