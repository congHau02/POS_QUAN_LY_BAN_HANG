package learn.java.billingsoftware.service;

import learn.java.billingsoftware.io.OrderRequest;
import learn.java.billingsoftware.io.OrderResponse;

import org.springframework.data.domain.Pageable;
import java.time.LocalDate;
import java.util.List;

public interface OrderService {
    OrderResponse createOrder(OrderRequest request);
    void deleteOrder(String orderId);
    List<OrderResponse> getLatestOrders();
    OrderResponse getOrderById(String orderId);

    Double sumSalesByDate(LocalDate date);
    Long countByOrderDate(LocalDate date);
    List<OrderResponse> findRecentOrders(int limit);
    learn.java.billingsoftware.io.OrderPageResponse getOrdersPaginated(int page, int size, String search, String status);

    OrderResponse cancelOrder(String orderId);
    OrderResponse switchToCash(String orderId);

    void handlePaymentWebhook(vn.payos.model.webhooks.WebhookData data);
}
