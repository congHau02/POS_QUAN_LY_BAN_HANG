package learn.java.billingsoftware;

import com.fasterxml.jackson.databind.ObjectMapper;
import learn.java.billingsoftware.entity.CategoryEntity;
import learn.java.billingsoftware.entity.ItemEntity;
import learn.java.billingsoftware.io.ModifierGroupRequest;
import learn.java.billingsoftware.io.ModifierRequest;
import learn.java.billingsoftware.repository.CategoryRepository;
import learn.java.billingsoftware.repository.ItemRepository;
import learn.java.billingsoftware.repository.ModifierGroupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class ModifierGroupIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ModifierGroupRepository modifierGroupRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ItemEntity testItem;

    @BeforeEach
    void setUp() {
        CategoryEntity category = CategoryEntity.builder()
                .categoryId("CAT-" + UUID.randomUUID().toString().substring(0, 8))
                .name("Drinks")
                .description("Beverages")
                .bgColor("#EEEEEE")
                .build();
        category = categoryRepository.save(category);

        testItem = ItemEntity.builder()
                .itemId("ITEM-" + UUID.randomUUID().toString().substring(0, 8))
                .name("Milk Tea")
                .description("Delicious Milk Tea")
                .price(new BigDecimal("35000.00"))
                .category(category)
                .build();
        testItem = itemRepository.save(testItem);
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void testCreateModifierGroupAndFetch() throws Exception {
        ModifierGroupRequest request = ModifierGroupRequest.builder()
                .name("Sugar Level")
                .description("Choose your sweetness")
                .minSelections(1)
                .maxSelections(1)
                .modifiers(List.of(
                        ModifierRequest.builder().name("0% Sugar").priceAdjustment(BigDecimal.ZERO).build(),
                        ModifierRequest.builder().name("50% Sugar").priceAdjustment(BigDecimal.ZERO).build(),
                        ModifierRequest.builder().name("100% Sugar").priceAdjustment(BigDecimal.ZERO).build()
                ))
                .build();

        String responseBody = mockMvc.perform(post("/admin/modifier-groups")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("Sugar Level")))
                .andExpect(jsonPath("$.minSelections", is(1)))
                .andExpect(jsonPath("$.maxSelections", is(1)))
                .andExpect(jsonPath("$.modifiers", hasSize(3)))
                .andReturn().getResponse().getContentAsString();

        // Also test GET /modifier-groups
        mockMvc.perform(get("/modifier-groups").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[?(@.name == 'Sugar Level')]", notNullValue()));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void testAttachModifierGroupToItemAndFetch() throws Exception {
        ModifierGroupRequest request = ModifierGroupRequest.builder()
                .name("Toppings")
                .description("Extra toppings")
                .minSelections(0)
                .maxSelections(3)
                .modifiers(List.of(
                        ModifierRequest.builder().name("Pearl").priceAdjustment(new BigDecimal("5000.00")).build(),
                        ModifierRequest.builder().name("Pudding").priceAdjustment(new BigDecimal("7000.00")).build()
                ))
                .build();

        String groupResponseStr = mockMvc.perform(post("/admin/modifier-groups")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String groupId = objectMapper.readTree(groupResponseStr).get("groupId").asText();

        // Attach to item
        mockMvc.perform(post("/admin/items/" + testItem.getItemId() + "/modifier-groups")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(groupId))))
                .andExpect(status().isOk());

        // Fetch item modifier groups
        mockMvc.perform(get("/items/" + testItem.getItemId() + "/modifier-groups").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("Toppings")))
                .andExpect(jsonPath("$[0].modifiers", hasSize(2)));

        // Fetch item itself and assert modifierGroups populated
        mockMvc.perform(get("/items/" + testItem.getItemId()).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modifierGroups", hasSize(1)))
                .andExpect(jsonPath("$.modifierGroups[0].name", is("Toppings")));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void testDeleteModifierGroup() throws Exception {
        ModifierGroupRequest request = ModifierGroupRequest.builder()
                .name("Ice Level")
                .description("Ice adjustment")
                .minSelections(1)
                .maxSelections(1)
                .modifiers(List.of(
                        ModifierRequest.builder().name("No Ice").priceAdjustment(BigDecimal.ZERO).build()
                ))
                .build();

        String groupResponseStr = mockMvc.perform(post("/admin/modifier-groups")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String groupId = objectMapper.readTree(groupResponseStr).get("groupId").asText();

        mockMvc.perform(delete("/admin/modifier-groups/" + groupId).with(user("admin").roles("ADMIN")))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/modifier-groups/" + groupId).with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void testCreateModifierGroupWithDefaultModifier() throws Exception {
        ModifierGroupRequest request = ModifierGroupRequest.builder()
                .name("Sweetness Choice")
                .description("Sweetness level")
                .minSelections(1)
                .maxSelections(1)
                .modifiers(List.of(
                        ModifierRequest.builder().name("50% Sugar").priceAdjustment(BigDecimal.ZERO).isDefault(false).build(),
                        ModifierRequest.builder().name("100% Sugar").priceAdjustment(BigDecimal.ZERO).isDefault(true).build()
                ))
                .build();

        mockMvc.perform(post("/admin/modifier-groups")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("Sweetness Choice")))
                .andExpect(jsonPath("$.modifiers[0].isDefault", is(false)))
                .andExpect(jsonPath("$.modifiers[1].isDefault", is(true)));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void testCreateModifierGroupExceedingMaxDefaultsThrowsBadRequest() throws Exception {
        ModifierGroupRequest request = ModifierGroupRequest.builder()
                .name("Ice Choice Invalid")
                .description("Exceeding default")
                .minSelections(1)
                .maxSelections(1)
                .modifiers(List.of(
                        ModifierRequest.builder().name("No Ice").priceAdjustment(BigDecimal.ZERO).isDefault(true).build(),
                        ModifierRequest.builder().name("Normal Ice").priceAdjustment(BigDecimal.ZERO).isDefault(true).build()
                ))
                .build();

        mockMvc.perform(post("/admin/modifier-groups")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
