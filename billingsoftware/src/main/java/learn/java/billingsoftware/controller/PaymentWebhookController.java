package learn.java.billingsoftware.controller;

import learn.java.billingsoftware.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import vn.payos.PayOS;
import vn.payos.exception.PayOSException;
import vn.payos.model.webhooks.Webhook;
import vn.payos.model.webhooks.WebhookData;

@Slf4j
@RestController
@RequestMapping("/payos")
@RequiredArgsConstructor
public class PaymentWebhookController {

    private final PayOS payOS;
    private final OrderService orderService;

    @PostMapping("/webhook")
    public ResponseEntity<?> handleWebhook(@RequestBody Webhook webhookBody) {
        try {
            // Xác thực webhook (SDK sẽ kiểm tra signature)
            WebhookData data = payOS.webhooks().verify(webhookBody);

            // Kiểm tra giao dịch thành công
            if ("00".equals(data.getCode())) {
                orderService.handlePaymentWebhook(data);
                return ResponseEntity.ok("success");
            }

            return ResponseEntity.badRequest().body("Giao dịch không thành công");

        } catch (PayOSException e) {
            log.warn("PayOS webhook verification failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Webhook verify failed: " + e.getMessage());
        } catch (ResponseStatusException e) {
            log.warn("PayOS webhook processing error: {}", e.getReason());
            return ResponseEntity.status(e.getStatusCode()).body(e.getReason());
        } catch (Exception e) {
            log.error("PayOS webhook unexpected error", e);
            return ResponseEntity.internalServerError().body("Lỗi xử lý webhook: " + e.getMessage());
        }
    }
}