package learn.java.billingsoftware;

import learn.java.billingsoftware.entity.*;
import learn.java.billingsoftware.io.InventoryTransactionRequest;
import learn.java.billingsoftware.io.ModifierGroupRequest;
import learn.java.billingsoftware.io.StockCheckRequest;
import learn.java.billingsoftware.repository.*;
import learn.java.billingsoftware.service.InventoryService;
import learn.java.billingsoftware.service.ModifierGroupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Transactional
public class AuditLogCompletenessIntegrationTest {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private ModifierGroupService modifierGroupService;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private VariantRepository variantRepository;

    @Autowired
    private ActivityLogRepository activityLogRepository;

    private ItemEntity testItem;
    private VariantEntity testVariant;

    @BeforeEach
    void setUp() {
        CategoryEntity category = categoryRepository.save(CategoryEntity.builder()
                .categoryId(UUID.randomUUID().toString())
                .name("Cat-" + UUID.randomUUID())
                .build());

        testItem = itemRepository.save(ItemEntity.builder()
                .itemId(UUID.randomUUID().toString())
                .name("Test Item")
                .price(BigDecimal.valueOf(100.0))
                .category(category)
                .build());

        testVariant = variantRepository.save(VariantEntity.builder()
                .variantId(UUID.randomUUID().toString())
                .sku("SKU-" + UUID.randomUUID())
                .item(testItem)
                .basePrice(BigDecimal.valueOf(100.0))
                .cachedStockQuantity(0)
                .build());
    }

    @Test
    void testRecordTransactionEmitsActivityLog() {
        InventoryTransactionRequest req = new InventoryTransactionRequest();
        req.setVariantId(testVariant.getVariantId());
        req.setTransactionType(TransactionType.IN);
        req.setQuantity(50);
        req.setNote("Import test");

        var response = inventoryService.recordTransaction(req);

        List<ActivityLogEntity> logs = activityLogRepository.findAll().stream()
                .filter(l -> "INVENTORY".equals(l.getEntityType()) && response.getTransactionId().equals(l.getEntityId()))
                .toList();

        assertEquals(1, logs.size());
        assertEquals("CREATE", logs.get(0).getAction());
    }

    @Test
    void testPerformStockCheckEmitsActivityLog() {
        StockCheckRequest req = new StockCheckRequest();
        req.setVariantId(testVariant.getVariantId());
        req.setActualCount(25);
        req.setNote("Stock count adjustment");

        var response = inventoryService.performStockCheck(req);

        List<ActivityLogEntity> logs = activityLogRepository.findAll().stream()
                .filter(l -> "INVENTORY".equals(l.getEntityType()) && response.getTransactionId().equals(l.getEntityId()))
                .toList();

        assertEquals(1, logs.size());
        assertEquals("UPDATE", logs.get(0).getAction());
    }

    @Test
    void testAttachModifierGroupsToItemEmitsActivityLog() {
        ModifierGroupRequest groupReq = new ModifierGroupRequest();
        groupReq.setName("Topping Group");
        var group = modifierGroupService.create(groupReq);

        modifierGroupService.attachToItem(testItem.getItemId(), List.of(group.getGroupId()));

        List<ActivityLogEntity> logs = activityLogRepository.findAll().stream()
                .filter(l -> "MODIFIER_GROUP".equals(l.getEntityType()) && testItem.getItemId().equals(l.getEntityId()))
                .filter(l -> "UPDATE".equals(l.getAction()))
                .toList();

        assertEquals(1, logs.size());
    }
}
