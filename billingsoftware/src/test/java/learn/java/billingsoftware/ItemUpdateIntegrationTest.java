package learn.java.billingsoftware;

import com.fasterxml.jackson.databind.ObjectMapper;
import learn.java.billingsoftware.entity.CategoryEntity;
import learn.java.billingsoftware.entity.ModifierGroupEntity;
import learn.java.billingsoftware.io.ItemRequest;
import learn.java.billingsoftware.io.ItemResponse;
import learn.java.billingsoftware.io.VariantRequest;
import learn.java.billingsoftware.repository.ActivityLogRepository;
import learn.java.billingsoftware.repository.CategoryRepository;
import learn.java.billingsoftware.repository.ModifierGroupRepository;
import learn.java.billingsoftware.service.FileUploadService;
import learn.java.billingsoftware.service.ItemService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class ItemUpdateIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ItemService itemService;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ModifierGroupRepository modifierGroupRepository;

    @Autowired
    private ActivityLogRepository activityLogRepository;

    @MockitoBean
    private FileUploadService fileUploadService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private CategoryEntity categoryA;
    private CategoryEntity categoryB;
    private ModifierGroupEntity modGroup;

    @BeforeEach
    void setUp() {
        when(fileUploadService.uploadFile(any())).thenReturn("https://test-s3.com/new-item-image.png");
        when(fileUploadService.deleteFile(any())).thenReturn(true);

        categoryA = categoryRepository.save(CategoryEntity.builder()
                .categoryId("cat-a-" + UUID.randomUUID().toString().substring(0, 6))
                .name("Cat A " + UUID.randomUUID().toString().substring(0, 6))
                .description("Category A")
                .bgColor("#111111")
                .build());

        categoryB = categoryRepository.save(CategoryEntity.builder()
                .categoryId("cat-b-" + UUID.randomUUID().toString().substring(0, 6))
                .name("Cat B " + UUID.randomUUID().toString().substring(0, 6))
                .description("Category B")
                .bgColor("#222222")
                .build());

        modGroup = modifierGroupRepository.save(ModifierGroupEntity.builder()
                .groupId("mod-grp-" + UUID.randomUUID().toString().substring(0, 6))
                .name("Sugar Level " + UUID.randomUUID().toString().substring(0, 6))
                .minSelections(0)
                .maxSelections(1)
                .build());
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DisplayName("Admin can update item via JSON PUT, modifies fields, variants, modifier groups and emits UPDATE activity log")
    void testUpdateItemJson() throws Exception {
        String sku1 = "SKU-T1-" + UUID.randomUUID().toString().substring(0, 6);
        ItemRequest createReq = ItemRequest.builder()
                .name("Matcha Latte")
                .description("Fresh Matcha")
                .price(new BigDecimal("45000.00"))
                .categoryId(categoryA.getCategoryId())
                .variants(List.of(VariantRequest.builder()
                        .sku(sku1)
                        .basePrice(new BigDecimal("45000.00"))
                        .attributes(Map.of("Size", "M"))
                        .build()))
                .build();

        ItemResponse created = itemService.add(createReq, null);
        String itemId = created.getItemId();

        // Non-admin rejected with 403 Forbidden
        ItemRequest updateReq = ItemRequest.builder()
                .name("Matcha Latte Special")
                .description("Premium Uji Matcha")
                .price(new BigDecimal("50000.00"))
                .categoryId(categoryB.getCategoryId())
                .modifierGroupIds(List.of(modGroup.getGroupId()))
                .variants(List.of(
                        VariantRequest.builder()
                                .sku(sku1)
                                .basePrice(new BigDecimal("50000.00"))
                                .attributes(Map.of("Size", "M"))
                                .build(),
                        VariantRequest.builder()
                                .sku("SKU-T2-" + UUID.randomUUID().toString().substring(0, 6))
                                .basePrice(new BigDecimal("60000.00"))
                                .attributes(Map.of("Size", "L"))
                                .build()
                ))
                .build();

        mockMvc.perform(put("/admin/items/" + itemId)
                .with(user("staff@billing.com").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isForbidden());

        // Admin updates successfully
        mockMvc.perform(put("/admin/items/" + itemId)
                .with(user("admin@billing.com").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemId").value(itemId))
                .andExpect(jsonPath("$.name").value("Matcha Latte Special"))
                .andExpect(jsonPath("$.description").value("Premium Uji Matcha"))
                .andExpect(jsonPath("$.categoryId").value(categoryB.getCategoryId()))
                .andExpect(jsonPath("$.categoryName").value(categoryB.getName()))
                .andExpect(jsonPath("$.variants.length()").value(2))
                .andExpect(jsonPath("$.modifierGroups.length()").value(1));

        boolean logged = activityLogRepository.findAll().stream().anyMatch(l ->
                "UPDATE".equalsIgnoreCase(l.getAction()) &&
                "ITEM".equalsIgnoreCase(l.getEntityType()) &&
                itemId.equals(l.getEntityId()) &&
                "admin@billing.com".equals(l.getUserEmail()));
        assertThat(logged).isTrue();
    }

    @Test
    @DisplayName("Admin can update item via multipart PUT, replaces image when file is provided and preserves image when omitted")
    void testUpdateItemMultipart() throws Exception {
        ItemRequest initialReq = ItemRequest.builder()
                .name("Cold Brew")
                .description("Steeped for 24h")
                .price(new BigDecimal("40000.00"))
                .categoryId(categoryA.getCategoryId())
                .build();

        MockMultipartFile file1 = new MockMultipartFile("file", "coldbrew.png", "image/png", "brew-bytes".getBytes());
        ItemResponse created = itemService.add(initialReq, file1);
        String itemId = created.getItemId();
        String initialImg = created.getImgUrl();
        assertThat(initialImg).isNotNull();

        // 1. Multipart update without file keeps existing image URL
        ItemRequest updateReqWithoutFile = ItemRequest.builder()
                .name("Cold Brew Gold")
                .price(new BigDecimal("42000.00"))
                .categoryId(categoryA.getCategoryId())
                .build();

        MockMultipartFile itemPartWithoutFile = new MockMultipartFile(
                "item",
                "",
                "application/json",
                objectMapper.writeValueAsBytes(updateReqWithoutFile)
        );

        mockMvc.perform(multipart(HttpMethod.PUT, "/admin/items/" + itemId)
                .file(itemPartWithoutFile)
                .with(user("admin@billing.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cold Brew Gold"))
                .andExpect(jsonPath("$.imgUrl").value(initialImg));

        // 2. Multipart update with new file uploads and updates image URL
        MockMultipartFile newFile = new MockMultipartFile("file", "coldbrew-new.png", "image/png", "new-brew-bytes".getBytes());
        mockMvc.perform(multipart(HttpMethod.PUT, "/admin/items/" + itemId)
                .file(itemPartWithoutFile)
                .file(newFile)
                .with(user("admin@billing.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cold Brew Gold"))
                .andExpect(jsonPath("$.imgUrl").value("https://test-s3.com/new-item-image.png"));
    }

    @Test
    @DisplayName("Updating non-existent item returns 404 Not Found")
    void testUpdateNonExistentItemReturns404() throws Exception {
        ItemRequest req = ItemRequest.builder()
                .name("Ghost Item")
                .price(new BigDecimal("10000.00"))
                .build();

        mockMvc.perform(put("/admin/items/" + UUID.randomUUID())
                .with(user("admin@billing.com").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }
}
