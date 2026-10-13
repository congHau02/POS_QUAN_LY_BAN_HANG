package learn.java.billingsoftware.service.impl;

import learn.java.billingsoftware.entity.CustomerEntity;
import learn.java.billingsoftware.entity.OrderEntity;
import learn.java.billingsoftware.entity.OrderItemEntity;
import learn.java.billingsoftware.entity.PromotionEntity;
import learn.java.billingsoftware.io.*;
import learn.java.billingsoftware.repository.CustomerRepository;
import learn.java.billingsoftware.repository.OrderEntityRepository;
import learn.java.billingsoftware.repository.PromotionRepository;
import learn.java.billingsoftware.service.ActivityLogService;
import learn.java.billingsoftware.service.OrderService;
import learn.java.billingsoftware.service.PromotionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import vn.payos.PayOS;
import vn.payos.exception.PayOSException;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.v2.paymentRequests.PaymentLinkItem;
import vn.payos.model.webhooks.WebhookData;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import learn.java.billingsoftware.entity.InventoryTransactionEntity;
import learn.java.billingsoftware.entity.TransactionType;
import learn.java.billingsoftware.entity.VariantEntity;
import learn.java.billingsoftware.repository.InventoryTransactionRepository;
import learn.java.billingsoftware.repository.VariantRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {
    private final OrderEntityRepository orderEntityRepository;
    private final PayOS payOS;
    private final VariantRepository variantRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final PromotionService promotionService;
    private final PromotionRepository promotionRepository;
    private final CustomerRepository customerRepository;
    private final ActivityLogService activityLogService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${payos.return-url:http://localhost:5173/payment/success}")
    private String payosReturnUrl;

    @Value("${payos.cancel-url:http://localhost:5173/payment/cancel}")
    private String payosCancelUrl;

    @Override
    @Transactional
    public OrderResponse createOrder(OrderRequest request) {
        // 1. Server-side Promotion Evaluation & Totals Recalculation
        List<EvaluationCartItem> evalCartItems = new ArrayList<>();
        if (request.getCartItems() != null) {
            for (OrderRequest.OrderItemRequest item : request.getCartItems()) {
                evalCartItems.add(EvaluationCartItem.builder()
                        .itemId(item.getItemId())
                        .variantId(item.getVariantId())
                        .name(item.getName())
                        .basePrice(item.getBasePrice() != null ? BigDecimal.valueOf(item.getBasePrice()) : null)
                        .price(item.getPrice() != null ? BigDecimal.valueOf(item.getPrice()) : null)
                        .quantity(item.getQuantity())
                        .selectedModifiers(item.getSelectedModifiers())
                        .build());
            }
        }

        PromotionEvaluationRequest evalRequest = PromotionEvaluationRequest.builder()
                .couponCode(request.getCouponCode())
                .cartItems(evalCartItems)
                .build();

        PromotionEvaluationResponse evalResponse = promotionService.evaluatePromotion(evalRequest);

        // 2. Build Order Entity with server-recalculated values
        OrderEntity newOrder = convertToOrderEntity(request);
        newOrder.setSubtotal(evalResponse.getSubtotal() != null ? evalResponse.getSubtotal().doubleValue() : 0.0);
        newOrder.setDiscountAmount(evalResponse.getDiscountAmount() != null ? evalResponse.getDiscountAmount().doubleValue() : 0.0);
        newOrder.setTax(evalResponse.getTax() != null ? evalResponse.getTax().doubleValue() : 0.0);
        newOrder.setGrandTotal(evalResponse.getGrandTotal() != null ? evalResponse.getGrandTotal().doubleValue() : 0.0);
        newOrder.setPromotionId(evalResponse.getAppliedPromotionId());
        newOrder.setPromotionName(evalResponse.getAppliedPromotionName());

        // 3. Customer Linking & Lifetime CRM Metrics Update
        CustomerEntity customer = null;
        if (request.getCustomerId() != null && !request.getCustomerId().trim().isEmpty()) {
            customer = customerRepository.findByCustomerId(request.getCustomerId().trim()).orElse(null);
        }
        if (customer == null && request.getPhoneNumber() != null && !request.getPhoneNumber().trim().isEmpty() && !"0000000000".equals(request.getPhoneNumber().trim())) {
            customer = customerRepository.findByPhoneNumber(request.getPhoneNumber().trim()).orElse(null);
        }

        if (customer != null) {
            newOrder.setCustomerId(customer.getCustomerId());
            newOrder.setCustomerName(customer.getName());
            newOrder.setPhoneNumber(customer.getPhoneNumber());

            int updatedOrderCount = (customer.getOrderCount() != null ? customer.getOrderCount() : 0) + 1;
            customer.setOrderCount(updatedOrderCount);
            double currentSpent = customer.getTotalSpent() != null ? customer.getTotalSpent() : 0.0;
            customer.setTotalSpent(currentSpent + (newOrder.getGrandTotal() != null ? newOrder.getGrandTotal() : 0.0));
            customerRepository.save(customer);
        } else {
            newOrder.setCustomerId(null);
            if (newOrder.getCustomerName() == null || newOrder.getCustomerName().trim().isEmpty()) {
                newOrder.setCustomerName("Người dùng mặc định");
            }
            if (newOrder.getPhoneNumber() == null || newOrder.getPhoneNumber().trim().isEmpty()) {
                newOrder.setPhoneNumber("0000000000");
            }
        }

        // 4. Atomic Promotion Usage Counter Increment & Limit Enforcement
        if (evalResponse.getAppliedPromotionId() != null) {
            PromotionEntity promo = promotionRepository.findByPromotionId(evalResponse.getAppliedPromotionId()).orElse(null);
            if (promo != null) {
                if (promo.getUsageLimit() != null && promo.getTimesUsed() != null && promo.getTimesUsed() >= promo.getUsageLimit()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã giảm giá đã đạt giới hạn lượt sử dụng");
                }
                promo.setTimesUsed((promo.getTimesUsed() != null ? promo.getTimesUsed() : 0) + 1);
                promotionRepository.save(promo);
            }
        }

        // 5. Payment details setup
        PaymentDetails paymentDetails = new PaymentDetails();
        paymentDetails.setStatus(newOrder.getPaymentMethod() == PaymentMethod.CASH ?
                PaymentDetails.PaymentStatus.COMPLETED : PaymentDetails.PaymentStatus.PENDING);
        newOrder.setPaymentDetails(paymentDetails);

        // 6. Order items mapping
        List<OrderItemEntity> orderItems = request.getCartItems() != null ? request.getCartItems().stream()
                .map(this::convertToOrderItemEntity)
                .collect(Collectors.toList()) : new ArrayList<>();

        OrderEntity finalNewOrder = newOrder;
        orderItems.forEach(item -> item.setOrder(finalNewOrder));
        newOrder.setItems(orderItems);

        // Save order to generate ID
        newOrder = orderEntityRepository.save(newOrder);

        // 7. Inventory deduction ledger sync
        for (OrderItemEntity item : newOrder.getItems()) {
            VariantEntity variant = null;
            if (item.getVariantId() != null && !item.getVariantId().trim().isEmpty()) {
                variant = variantRepository.findByVariantId(item.getVariantId()).orElse(null);
            } else if (item.getItemId() != null) {
                List<VariantEntity> variants = variantRepository.findByItem_ItemId(item.getItemId());
                if (!variants.isEmpty()) {
                    variant = variants.get(0);
                }
            }

            if (variant != null) {
                int soldQuantity = item.getQuantity() != null ? item.getQuantity() : 1;
                InventoryTransactionEntity transaction = InventoryTransactionEntity.builder()
                        .transactionId(UUID.randomUUID().toString())
                        .variant(variant)
                        .transactionType(TransactionType.OUT)
                        .quantity(-Math.abs(soldQuantity))
                        .referenceId(newOrder.getOrderId())
                        .note("POS Sale - Order " + newOrder.getOrderId())
                        .build();
                inventoryTransactionRepository.saveAndFlush(transaction);

                Integer updatedStock = inventoryTransactionRepository.calculateStockByVariantId(variant.getVariantId());
                variant.setCachedStockQuantity(updatedStock != null ? updatedStock : 0);
                variantRepository.save(variant);
            }
        }

        // 8. PayOS Payment Link handling
        if (newOrder.getPaymentMethod() == PaymentMethod.PAYOS) {
            try {
                long orderCode = newOrder.getId();

                List<PaymentLinkItem> payOSItems = newOrder.getItems().stream()
                        .map(item -> PaymentLinkItem.builder()
                                .name(item.getName())
                                .price(item.getPrice().longValue())
                                .quantity(item.getQuantity())
                                .build())
                        .collect(Collectors.toList());

                CreatePaymentLinkRequest paymentRequest = CreatePaymentLinkRequest.builder()
                        .orderCode(orderCode)
                        .amount(newOrder.getGrandTotal().longValue())
                        .description("Thanh toan don " + orderCode)
                        .returnUrl(payosReturnUrl)
                        .cancelUrl(payosCancelUrl)
                        .items(payOSItems)
                        .build();

                CreatePaymentLinkResponse payOSResponse = payOS.paymentRequests().create(paymentRequest);

                newOrder.getPaymentDetails().setPaymentLinkId(payOSResponse.getPaymentLinkId());
                newOrder.getPaymentDetails().setCheckoutUrl(payOSResponse.getCheckoutUrl());
                newOrder.getPaymentDetails().setQrCode(payOSResponse.getQrCode());
                newOrder.getPaymentDetails().setOrderId(newOrder.getOrderId());

                orderEntityRepository.save(newOrder);

            } catch (PayOSException e) {
                throw new RuntimeException("Lỗi khi tạo mã thanh toán PayOS: " + e.getMessage(), e);
            } catch (Exception e) {
                throw new RuntimeException("Lỗi khi tạo mã thanh toán PayOS: " + e.getMessage(), e);
            }
        }

        activityLogService.logActivity("CREATE", "ORDER", newOrder.getOrderId(), "Created order #" + newOrder.getOrderId() + " (" + newOrder.getGrandTotal() + " VND)");
        return convertToResponse(newOrder);
    }

    private OrderItemEntity convertToOrderItemEntity(OrderRequest.OrderItemRequest orderItemRequest) {
        String modifiersJson = null;
        if (orderItemRequest.getSelectedModifiers() != null && !orderItemRequest.getSelectedModifiers().isEmpty()) {
            try {
                modifiersJson = objectMapper.writeValueAsString(orderItemRequest.getSelectedModifiers());
            } catch (Exception ignored) {
            }
        }

        return OrderItemEntity.builder()
                .itemId(orderItemRequest.getItemId())
                .variantId(orderItemRequest.getVariantId())
                .name(orderItemRequest.getName())
                .basePrice(orderItemRequest.getBasePrice() != null ? orderItemRequest.getBasePrice() : orderItemRequest.getPrice())
                .price(orderItemRequest.getPrice())
                .quantity(orderItemRequest.getQuantity())
                .selectedModifiers(modifiersJson)
                .build();
    }

    private OrderResponse convertToResponse(OrderEntity newOrder) {
        return OrderResponse.builder()
                .orderId(newOrder.getOrderId())
                .customerId(newOrder.getCustomerId())
                .customerName(newOrder.getCustomerName())
                .phoneNumber(newOrder.getPhoneNumber())
                .subtotal(newOrder.getSubtotal())
                .discountAmount(newOrder.getDiscountAmount() != null ? newOrder.getDiscountAmount() : 0.0)
                .tax(newOrder.getTax())
                .grandTotal(newOrder.getGrandTotal())
                .promotionId(newOrder.getPromotionId())
                .promotionName(newOrder.getPromotionName())
                .paymentMethod(newOrder.getPaymentMethod())
                .items(newOrder.getItems().stream()
                        .map(this::convertToItemResponse)
                        .collect(Collectors.toList()))
                .paymentDetails(newOrder.getPaymentDetails())
                .createdAt(newOrder.getCreatedAt())
                .build();
    }

    private OrderResponse.OrderItemResponse convertToItemResponse(OrderItemEntity orderItemEntity) {
        List<SelectedModifier> selectedModifiers = new ArrayList<>();
        if (orderItemEntity.getSelectedModifiers() != null && !orderItemEntity.getSelectedModifiers().trim().isEmpty()) {
            try {
                selectedModifiers = objectMapper.readValue(
                        orderItemEntity.getSelectedModifiers(),
                        new TypeReference<List<SelectedModifier>>() {});
            } catch (Exception ignored) {
            }
        }

        return OrderResponse.OrderItemResponse.builder()
                .itemId(orderItemEntity.getItemId())
                .variantId(orderItemEntity.getVariantId())
                .name(orderItemEntity.getName())
                .basePrice(orderItemEntity.getBasePrice())
                .price(orderItemEntity.getPrice())
                .quantity(orderItemEntity.getQuantity())
                .selectedModifiers(selectedModifiers)
                .build();
    }

    private OrderEntity convertToOrderEntity(OrderRequest request) {
        return OrderEntity.builder()
                .customerId(request.getCustomerId())
                .customerName(request.getCustomerName())
                .phoneNumber(request.getPhoneNumber())
                .subtotal(request.getSubtotal())
                .discountAmount(request.getDiscountAmount() != null ? request.getDiscountAmount() : 0.0)
                .tax(request.getTax())
                .grandTotal(request.getGrandTotal())
                .promotionId(request.getAppliedPromotionId())
                .paymentMethod(request.getPaymentMethod() != null ? PaymentMethod.valueOf(request.getPaymentMethod()) : PaymentMethod.CASH)
                .build();
    }

    @Override
    @Transactional
    public void deleteOrder(String orderId) {
        OrderEntity existingOrder = orderEntityRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng #" + orderId));

        PaymentDetails paymentDetails = existingOrder.getPaymentDetails();
        PaymentDetails.PaymentStatus status = paymentDetails != null ? paymentDetails.getStatus() : null;

        if (status == PaymentDetails.PaymentStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không thể xóa đơn hàng đã hoàn tất (COMPLETED). Vui lòng kiểm tra lại lịch sử kế toán.");
        }

        if (status != PaymentDetails.PaymentStatus.PENDING && status != PaymentDetails.PaymentStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chỉ cho phép xóa các đơn hàng ở trạng thái PENDING hoặc CANCELLED.");
        }

        // Bù trừ nếu đơn đang ở trạng thái PENDING (chưa hủy)
        if (status == PaymentDetails.PaymentStatus.PENDING) {
            compensatePendingOrder(existingOrder, "Deleted Order #" + existingOrder.getOrderId(), "Đơn hàng đã bị xóa");
        }

        orderEntityRepository.delete(existingOrder);
        activityLogService.logActivity("DELETE", "ORDER", existingOrder.getOrderId(), "Deleted order #" + existingOrder.getOrderId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getLatestOrders() {
        return orderEntityRepository.findAllByOrderByCreatedAtDesc() 
                .stream() 
                .map(this::convertToResponse) 
                .collect(Collectors.toList()); 
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(String orderId) {
        OrderEntity existingOrder = orderEntityRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng #" + orderId));
        return convertToResponse(existingOrder);
    }

    @Override
    @Transactional(readOnly = true)
    public Double sumSalesByDate(LocalDate date) {
        return orderEntityRepository.sumSalesByDate(date);
    }

    @Override
    @Transactional(readOnly = true)
    public Long countByOrderDate(LocalDate date) {
        return orderEntityRepository.countByOrderDate(date);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> findRecentOrders(int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return orderEntityRepository.findRecentOrders(pageable)
                .stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public OrderPageResponse getOrdersPaginated(int page, int size, String search, String status) {
        PaymentDetails.PaymentStatus paymentStatus = null;
        if (status != null && !status.trim().isEmpty() && !status.equalsIgnoreCase("ALL")) {
            try {
                paymentStatus = PaymentDetails.PaymentStatus.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
            }
        }

        String cleanSearch = (search != null && !search.trim().isEmpty()) ? search.trim() : null;
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        org.springframework.data.domain.Page<OrderEntity> orderPage = orderEntityRepository.findOrdersWithFilter(cleanSearch, paymentStatus, pageable);

        List<OrderResponse> content = orderPage.getContent().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());

        return OrderPageResponse.builder()
                .content(content)
                .totalElements(orderPage.getTotalElements())
                .totalPages(orderPage.getTotalPages())
                .currentPage(page)
                .pageSize(size)
                .build();
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(String orderId) {
        OrderEntity order = orderEntityRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng #" + orderId));

        PaymentDetails paymentDetails = order.getPaymentDetails();
        if (paymentDetails == null || paymentDetails.getStatus() != PaymentDetails.PaymentStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chỉ có thể hủy đơn hàng đang ở trạng thái Chờ xử lý (PENDING)");
        }

        // 1. Cập nhật trạng thái CANCELLED
        paymentDetails.setStatus(PaymentDetails.PaymentStatus.CANCELLED);
        order.setPaymentDetails(paymentDetails);

        // 2. Bù trừ toàn diện (Kho, Promotion, CRM, PayOS)
        compensatePendingOrder(order, "Cancelled Order #" + order.getOrderId(), "Khách hàng hủy đơn hàng");

        order = orderEntityRepository.save(order);
        activityLogService.logActivity("CANCEL", "ORDER", order.getOrderId(), "Cancelled order #" + order.getOrderId());

        return convertToResponse(order);
    }

    @Override
    @Transactional
    public OrderResponse switchToCash(String orderId) {
        OrderEntity order = orderEntityRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng #" + orderId));

        PaymentDetails paymentDetails = order.getPaymentDetails();
        if (paymentDetails == null || paymentDetails.getStatus() != PaymentDetails.PaymentStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chỉ có thể chuyển sang tiền mặt cho đơn hàng đang ở trạng thái Chờ xử lý (PENDING)");
        }

        // 1. Hủy link PayOS từ xa nếu là PAYOS
        cancelRemotePayOSPayment(order, "Chuyển sang thanh toán tiền mặt");

        // 2. Chuyển đổi sang CASH và COMPLETED
        order.setPaymentMethod(PaymentMethod.CASH);
        paymentDetails.setStatus(PaymentDetails.PaymentStatus.COMPLETED);
        order.setPaymentDetails(paymentDetails);

        order = orderEntityRepository.save(order);
        activityLogService.logActivity("UPDATE", "ORDER", order.getOrderId(), "Switched payment method to CASH for order #" + order.getOrderId());

        return convertToResponse(order);
    }

    private void compensatePendingOrder(OrderEntity order, String ledgerNotePrefix, String payosCancelReason) {
        // 1. Bù trừ tồn kho qua giao dịch IN
        if (order.getItems() != null) {
            for (OrderItemEntity item : order.getItems()) {
                VariantEntity variant = null;
                if (item.getVariantId() != null && !item.getVariantId().trim().isEmpty()) {
                    variant = variantRepository.findByVariantId(item.getVariantId()).orElse(null);
                } else if (item.getItemId() != null) {
                    List<VariantEntity> variants = variantRepository.findByItem_ItemId(item.getItemId());
                    if (!variants.isEmpty()) {
                        variant = variants.get(0);
                    }
                }

                if (variant != null) {
                    int quantityToRevert = item.getQuantity() != null ? Math.abs(item.getQuantity()) : 1;
                    InventoryTransactionEntity transaction = InventoryTransactionEntity.builder()
                            .transactionId(UUID.randomUUID().toString())
                            .variant(variant)
                            .transactionType(TransactionType.IN)
                            .quantity(quantityToRevert)
                            .referenceId(order.getOrderId())
                            .note(ledgerNotePrefix + " - Stock Reversal")
                            .build();
                    inventoryTransactionRepository.saveAndFlush(transaction);

                    Integer updatedStock = inventoryTransactionRepository.calculateStockByVariantId(variant.getVariantId());
                    variant.setCachedStockQuantity(updatedStock != null ? updatedStock : 0);
                    variantRepository.save(variant);
                }
            }
        }

        // 2. Hoàn lại quota Promotion nếu có áp dụng
        if (order.getPromotionId() != null && !order.getPromotionId().trim().isEmpty()) {
            PromotionEntity promo = promotionRepository.findByPromotionId(order.getPromotionId()).orElse(null);
            if (promo != null && promo.getTimesUsed() != null && promo.getTimesUsed() > 0) {
                promo.setTimesUsed(promo.getTimesUsed() - 1);
                promotionRepository.save(promo);
            }
        }

        // 3. Hoàn lại số liệu CRM của khách hàng nếu có
        if (order.getCustomerId() != null && !order.getCustomerId().trim().isEmpty()) {
            CustomerEntity customer = customerRepository.findByCustomerId(order.getCustomerId()).orElse(null);
            if (customer != null) {
                customer.revertOrderSpending(order.getGrandTotal());
                customerRepository.save(customer);
            }
        }

        // 4. Hủy liên kết PayOS từ xa nếu là PAYOS
        cancelRemotePayOSPayment(order, payosCancelReason);
    }

    private void cancelRemotePayOSPayment(OrderEntity order, String reason) {
        if (order.getPaymentMethod() == PaymentMethod.PAYOS) {
            try {
                payOS.paymentRequests().cancel(order.getId(), reason);
            } catch (Exception e) {
                System.err.println("Warning: Không thể hủy link PayOS cho đơn #" + order.getId() + ": " + e.getMessage());
            }
        }
    }

    @Override
    @Transactional
    public void handlePaymentWebhook(WebhookData data) {
        if (data == null || data.getOrderCode() == null) {
            return;
        }

        Long orderCode = data.getOrderCode();
        if (orderCode == 123L) {
            log.info("PayOS Webhook test ping acknowledged (orderCode=123)");
            return;
        }

        OrderEntity order = orderEntityRepository.findById(orderCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng với orderCode: " + orderCode));

        PaymentDetails paymentDetails = order.getPaymentDetails();
        if (paymentDetails != null && paymentDetails.getStatus() == PaymentDetails.PaymentStatus.COMPLETED) {
            log.info("Order #{} is already COMPLETED. Ignoring redundant webhook delivery.", order.getOrderId());
            return;
        }

        if (paymentDetails == null) {
            paymentDetails = new PaymentDetails();
            order.setPaymentDetails(paymentDetails);
        }
        paymentDetails.setStatus(PaymentDetails.PaymentStatus.COMPLETED);

        orderEntityRepository.save(order);
        activityLogService.logActivity("PayOS-Webhook", "UPDATE", "ORDER", order.getOrderId(), "Payment confirmed via PayOS Webhook for order #" + order.getOrderId());
    }
}