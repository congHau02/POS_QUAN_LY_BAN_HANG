package learn.java.billingsoftware;

import learn.java.billingsoftware.entity.CategoryEntity;
import learn.java.billingsoftware.entity.ItemEntity;
import learn.java.billingsoftware.entity.VariantEntity;
import learn.java.billingsoftware.io.OrderRequest;
import learn.java.billingsoftware.io.PaymentMethod;
import learn.java.billingsoftware.repository.CategoryRepository;
import learn.java.billingsoftware.repository.ItemRepository;
import learn.java.billingsoftware.repository.VariantRepository;
import learn.java.billingsoftware.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.v2.paymentRequests.PaymentLinkStatus;
import vn.payos.service.blocking.v2.paymentRequests.PaymentRequestsService;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@TestPropertySource(properties = {
        "payos.return-url=https://custom-domain.com/payment/success",
        "payos.cancel-url=https://custom-domain.com/payment/cancel"
})
@Transactional
public class PaymentRedirectUrlsIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private VariantRepository variantRepository;

    @MockitoBean
    private PayOS payOS;

    @MockitoBean
    private PaymentRequestsService paymentRequestsService;

    private ItemEntity testItem;
    private VariantEntity testVariant;

    @BeforeEach
    void setUp() throws Exception {
        when(payOS.paymentRequests()).thenReturn(paymentRequestsService);

        CreatePaymentLinkResponse mockResponse = CreatePaymentLinkResponse.builder()
                .bin("970403")
                .accountNumber("123456789")
                .accountName("TEST STORE")
                .amount(50000L)
                .description("Thanh toan don test")
                .orderCode(12345L)
                .currency("VND")
                .paymentLinkId("LINK-12345")
                .status(PaymentLinkStatus.PENDING)
                .checkoutUrl("https://pay.payos.vn/web/test")
                .qrCode("000201010212...")
                .build();
        when(paymentRequestsService.create(any(CreatePaymentLinkRequest.class))).thenReturn(mockResponse);

        CategoryEntity category = categoryRepository.save(CategoryEntity.builder()
                .categoryId(UUID.randomUUID().toString())
                .name("Cat-" + UUID.randomUUID())
                .build());

        testItem = itemRepository.save(ItemEntity.builder()
                .itemId(UUID.randomUUID().toString())
                .name("Item Redirect Test")
                .price(BigDecimal.valueOf(50000.0))
                .category(category)
                .build());

        testVariant = variantRepository.save(VariantEntity.builder()
                .variantId(UUID.randomUUID().toString())
                .sku("SKU-" + UUID.randomUUID())
                .item(testItem)
                .basePrice(BigDecimal.valueOf(50000.0))
                .build());
    }

    @Test
    void testCreateOrderWithPayOSUsesConfiguredReturnAndCancelUrls() throws Exception {
        OrderRequest.OrderItemRequest itemReq = new OrderRequest.OrderItemRequest();
        itemReq.setItemId(testItem.getItemId());
        itemReq.setVariantId(testVariant.getVariantId());
        itemReq.setName(testItem.getName());
        itemReq.setPrice(50000.0);
        itemReq.setQuantity(1);

        OrderRequest request = new OrderRequest();
        request.setCustomerName("Redirect Test Customer");
        request.setPhoneNumber("0987654321");
        request.setPaymentMethod("PAYOS");
        request.setCartItems(List.of(itemReq));

        orderService.createOrder(request);

        ArgumentCaptor<CreatePaymentLinkRequest> captor = ArgumentCaptor.forClass(CreatePaymentLinkRequest.class);
        verify(paymentRequestsService).create(captor.capture());

        CreatePaymentLinkRequest captured = captor.getValue();
        assertEquals("https://custom-domain.com/payment/success", captured.getReturnUrl());
        assertEquals("https://custom-domain.com/payment/cancel", captured.getCancelUrl());
    }
}
