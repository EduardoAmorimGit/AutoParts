package com.autoparts_maven.autoparts_maven.returns;

import com.autoparts_maven.autoparts_maven.common.PageResponse;
import com.autoparts_maven.autoparts_maven.order.OrderService;
import com.autoparts_maven.autoparts_maven.order.OrderStatus;
import com.autoparts_maven.autoparts_maven.order.dto.OrderDtos.OrderItemResponse;
import com.autoparts_maven.autoparts_maven.order.dto.OrderDtos.OrderResponse;
import com.autoparts_maven.autoparts_maven.payment.PaymentService;
import com.autoparts_maven.autoparts_maven.product.Product;
import com.autoparts_maven.autoparts_maven.product.ProductRepository;
import com.autoparts_maven.autoparts_maven.returns.dto.ReturnDtos.CreateReturnRequest;
import com.autoparts_maven.autoparts_maven.returns.dto.ReturnDtos.ItemRequest;
import com.autoparts_maven.autoparts_maven.returns.dto.ReturnDtos.ReturnResponse;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Returns after delivery, following the Código de Defesa do Consumidor:
 *
 * <ul>
 *   <li>WITHDRAWAL (art. 49): until the end of the {@code returns.withdrawal-days}-th day after
 *       delivery (Rio time), no reason needed; refund of the items plus the delivery fee (once per order).
 *   <li>DEFECT (arts. 18 and 26): never blocked by a deadline here (hidden defects count from their
 *       discovery); a description is mandatory and the admins decide between refund and replacement.
 * </ul>
 *
 * The amount due is computed from the order snapshot when the request is opened.
 */
@Service
public class ReturnService {

    static final ZoneId RIO = ZoneId.of("America/Sao_Paulo");
    private static final EnumSet<ReturnStatus> NOT_CLAIMING = EnumSet.of(ReturnStatus.REJECTED);

    private final ReturnRequestRepository repository;
    private final OrderService orderService;
    private final PaymentService paymentService;
    private final ProductRepository productRepository;
    private final ApplicationEventPublisher events;
    private final int withdrawalDays;
    private final Duration answerDeadline;
    private final TransactionTemplate transactions;

    public ReturnService(ReturnRequestRepository repository, OrderService orderService, PaymentService paymentService,
            ProductRepository productRepository, ApplicationEventPublisher events, TransactionTemplate transactions,
            @Value("${returns.withdrawal-days:7}") int withdrawalDays,
            @Value("${returns.answer-deadline:P2D}") Duration answerDeadline) {
        if (withdrawalDays < 7) {
            throw new IllegalArgumentException("returns.withdrawal-days cannot be below the legal 7 days");
        }
        this.repository = repository;
        this.orderService = orderService;
        this.paymentService = paymentService;
        this.productRepository = productRepository;
        this.events = events;
        this.withdrawalDays = withdrawalDays;
        this.answerDeadline = answerDeadline;
        this.transactions = transactions;
    }

    @Transactional
    public ReturnResponse create(Long userId, Long orderId, CreateReturnRequest request) {
        // Locks the customer's order, so two requests for the same items cannot both pass the checks.
        orderService.lockForPayment(userId, orderId);
        OrderResponse order = orderService.getMine(userId, orderId);
        if (!OrderStatus.DELIVERED.name().equals(order.status())) {
            throw new ReturnNotAllowedException("Only delivered orders can be returned; to stop an order that was "
                    + "not delivered yet, cancel it");
        }
        Instant now = Instant.now();
        String reason = request.reason() == null || request.reason().isBlank() ? null : request.reason().trim();
        if (request.type() == ReturnType.WITHDRAWAL && now.isAfter(withdrawalDeadline(order.deliveredAt()))) {
            throw new ReturnNotAllowedException("The " + withdrawalDays + "-day withdrawal period ended; if the "
                    + "product has a defect, open a DEFECT return");
        }
        if (request.type() == ReturnType.DEFECT && reason == null) {
            throw new ReturnNotAllowedException("Describe the defect");
        }

        Map<Long, OrderItemResponse> orderItems = order.items().stream()
                .collect(Collectors.toMap(OrderItemResponse::id, Function.identity()));
        Map<Long, Long> claimed = claimed(orderId);
        Map<Long, Integer> requested = new HashMap<>();
        for (ItemRequest item : request.items()) {
            requested.merge(item.orderItemId(), item.quantity(), Integer::sum);
        }

        ReturnRequest entity = new ReturnRequest();
        BigDecimal itemsAmount = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> entry : requested.entrySet()) {
            OrderItemResponse item = orderItems.get(entry.getKey());
            if (item == null) {
                throw new ReturnNotAllowedException("Item " + entry.getKey() + " is not part of order " + orderId);
            }
            long available = item.quantity() - claimed.getOrDefault(item.id(), 0L);
            if (entry.getValue() > available) {
                throw new ReturnNotAllowedException("Only " + available + " unit(s) of '" + item.productName()
                        + "' can still be returned");
            }
            entity.addItem(item.id(), entry.getValue());
            itemsAmount = itemsAmount.add(item.unitPrice().multiply(BigDecimal.valueOf(entry.getValue())));
        }

        BigDecimal fee = request.type() == ReturnType.WITHDRAWAL && !feeAlreadyRefunded(orderId)
                ? order.deliveryFee() : BigDecimal.ZERO;
        entity.setOrderId(orderId);
        entity.setUserId(userId);
        entity.setType(request.type());
        entity.setStatus(ReturnStatus.REQUESTED);
        entity.setCustomerReason(reason);
        entity.setItemsAmount(itemsAmount);
        entity.setDeliveryFeeAmount(fee);
        entity.setRefundAmount(itemsAmount.add(fee));
        entity.setRequestedAt(now);
        ReturnRequest saved = repository.saveAndFlush(entity);
        events.publishEvent(new ReturnEvent(ReturnEvent.Type.REQUESTED, saved.getId()));
        return view(saved);
    }

    @Transactional(readOnly = true)
    public List<ReturnResponse> listMine(Long userId, Long orderId) {
        orderService.getMine(userId, orderId);
        return repository.findByOrderIdOrderByIdAsc(orderId).stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<ReturnResponse> search(ReturnStatus status, Pageable pageable) {
        return PageResponse.from(repository.search(status, pageable).map(this::view));
    }

    @Transactional(readOnly = true)
    public ReturnResponse get(Long id) {
        return view(find(id));
    }

    @Transactional
    public ReturnResponse approve(Long id, Long adminId, String note) {
        ReturnRequest request = lock(id);
        request.moveTo(ReturnStatus.APPROVED);
        request.setApprovedAt(Instant.now());
        decide(request, adminId, note);
        events.publishEvent(new ReturnEvent(ReturnEvent.Type.APPROVED, id));
        return view(request);
    }

    /** The items are back; with {@code restock} they return to the shelf (not for defective units). */
    @Transactional
    public ReturnResponse receive(Long id, Long adminId, boolean restock, String note) {
        ReturnRequest request = lock(id);
        request.moveTo(ReturnStatus.RECEIVED);
        request.setReceivedAt(Instant.now());
        decide(request, adminId, note);
        if (restock) {
            restock(request);
        }
        events.publishEvent(new ReturnEvent(ReturnEvent.Type.RECEIVED, id));
        return view(request);
    }

    /**
     * Refunds the amount due through the payment provider (outside this transaction), then closes
     * the request. If the provider fails the request stays open and the admin can retry safely.
     */
    public ReturnResponse refund(Long id, Long adminId) {
        ReturnRequest request = find(id);
        if (!request.getStatus().canMoveTo(ReturnStatus.REFUNDED)) {
            throw new InvalidReturnTransitionException(id, request.getStatus(), ReturnStatus.REFUNDED);
        }
        paymentService.refundForReturn(request.getOrderId(), id, request.getRefundAmount(), adminId);
        return transactions.execute(status -> {
            ReturnRequest locked = lock(id);
            if (locked.getStatus() != ReturnStatus.REFUNDED) {
                locked.moveTo(ReturnStatus.REFUNDED);
                locked.setResolvedAt(Instant.now());
                locked.setDecidedBy(adminId);
            }
            return view(locked);
        });
    }

    /** A defect solved with a new unit (sent by hand); no money moves. */
    @Transactional
    public ReturnResponse replace(Long id, Long adminId, String note) {
        ReturnRequest request = lock(id);
        if (request.getType() != ReturnType.DEFECT) {
            throw new ReturnNotAllowedException("A withdrawal is always refunded, never replaced");
        }
        request.moveTo(ReturnStatus.REPLACED);
        request.setResolvedAt(Instant.now());
        decide(request, adminId, note);
        events.publishEvent(new ReturnEvent(ReturnEvent.Type.REPLACED, id));
        return view(request);
    }

    @Transactional
    public ReturnResponse reject(Long id, Long adminId, String reason) {
        ReturnRequest request = lock(id);
        request.moveTo(ReturnStatus.REJECTED);
        request.setRejectionReason(reason.trim());
        request.setResolvedAt(Instant.now());
        request.setDecidedBy(adminId);
        events.publishEvent(new ReturnEvent(ReturnEvent.Type.REJECTED, id));
        return view(request);
    }

    /** End of the last day of the withdrawal period, in Rio time. */
    Instant withdrawalDeadline(Instant deliveredAt) {
        return deliveredAt.atZone(RIO).toLocalDate().plusDays(withdrawalDays)
                .atTime(LocalTime.MAX).atZone(RIO).toInstant();
    }

    private Map<Long, Long> claimed(Long orderId) {
        Map<Long, Long> claimed = new HashMap<>();
        for (Object[] row : repository.claimedQuantities(orderId, NOT_CLAIMING)) {
            claimed.put((Long) row[0], ((Number) row[1]).longValue());
        }
        return claimed;
    }

    private boolean feeAlreadyRefunded(Long orderId) {
        return repository.findByOrderIdOrderByIdAsc(orderId).stream()
                .anyMatch(r -> r.getStatus() != ReturnStatus.REJECTED && r.getDeliveryFeeAmount().signum() > 0);
    }

    private void restock(ReturnRequest request) {
        OrderResponse order = orderService.get(request.getOrderId());
        Map<Long, Long> productByItem = order.items().stream().filter(i -> i.productId() != null)
                .collect(Collectors.toMap(OrderItemResponse::id, OrderItemResponse::productId));
        List<Long> productIds = request.getItems().stream().map(i -> productByItem.get(i.getOrderItemId()))
                .filter(Objects::nonNull).distinct().sorted().toList();
        Map<Long, Product> locked = productRepository.findAllForUpdate(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        for (ReturnRequest.ReturnRequestItem item : request.getItems()) {
            Product product = locked.get(productByItem.get(item.getOrderItemId()));
            if (product != null) {
                product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
            }
        }
        request.setRestocked(true);
    }

    private void decide(ReturnRequest request, Long adminId, String note) {
        request.setDecidedBy(adminId);
        if (note != null && !note.isBlank()) {
            request.setAdminNote(note.trim());
        }
    }

    private ReturnRequest lock(Long id) {
        return repository.findForUpdate(id).orElseThrow(() -> new ReturnNotFoundException(id));
    }

    private ReturnRequest find(Long id) {
        return repository.findById(id).orElseThrow(() -> new ReturnNotFoundException(id));
    }

    private ReturnResponse view(ReturnRequest request) {
        boolean overdue = request.getStatus() == ReturnStatus.REQUESTED
                && request.getRequestedAt().plus(answerDeadline).isBefore(Instant.now());
        Instant legalDeadline = request.getType() == ReturnType.DEFECT && request.getStatus().isOpen()
                ? request.getRequestedAt().plus(Duration.ofDays(30)) : null;
        return ReturnResponse.from(request, overdue, legalDeadline);
    }
}
