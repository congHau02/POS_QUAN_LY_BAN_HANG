package learn.java.billingsoftware;

import jakarta.persistence.EntityManager;
import learn.java.billingsoftware.entity.CategoryEntity;
import learn.java.billingsoftware.entity.ItemEntity;
import learn.java.billingsoftware.entity.ModifierEntity;
import learn.java.billingsoftware.entity.ModifierGroupEntity;
import learn.java.billingsoftware.repository.CategoryRepository;
import learn.java.billingsoftware.repository.ItemRepository;
import learn.java.billingsoftware.repository.ModifierGroupRepository;
import learn.java.billingsoftware.repository.ModifierRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
public class EntityUpdateTimestampIntegrationTest {

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ModifierGroupRepository modifierGroupRepository;

    @Autowired
    private ModifierRepository modifierRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void testItemEntityUpdatedAtAdvancesOnUpdate() throws InterruptedException {
        CategoryEntity category = categoryRepository.saveAndFlush(CategoryEntity.builder()
                .categoryId(UUID.randomUUID().toString())
                .name("Cat-" + UUID.randomUUID())
                .build());

        ItemEntity item = itemRepository.saveAndFlush(ItemEntity.builder()
                .itemId(UUID.randomUUID().toString())
                .name("Original Item")
                .price(BigDecimal.valueOf(100.0))
                .category(category)
                .build());

        entityManager.flush();
        entityManager.clear();

        ItemEntity loaded = itemRepository.findById(item.getId()).orElseThrow();
        Timestamp initialUpdatedAt = loaded.getUpdatedAt();
        assertNotNull(initialUpdatedAt);

        Thread.sleep(100);

        loaded.setName("Updated Item Name");
        itemRepository.saveAndFlush(loaded);
        entityManager.clear();

        ItemEntity updated = itemRepository.findById(item.getId()).orElseThrow();
        assertTrue(updated.getUpdatedAt().after(initialUpdatedAt),
                "Item updatedAt should be updated when entity changes");
    }

    @Test
    void testModifierGroupEntityUpdatedAtAdvancesOnUpdate() throws InterruptedException {
        ModifierGroupEntity group = modifierGroupRepository.saveAndFlush(ModifierGroupEntity.builder()
                .groupId(UUID.randomUUID().toString())
                .name("Original Group")
                .build());

        entityManager.flush();
        entityManager.clear();

        ModifierGroupEntity loaded = modifierGroupRepository.findById(group.getId()).orElseThrow();
        Timestamp initialUpdatedAt = loaded.getUpdatedAt();
        assertNotNull(initialUpdatedAt);

        Thread.sleep(100);

        loaded.setName("Updated Group Name");
        modifierGroupRepository.saveAndFlush(loaded);
        entityManager.clear();

        ModifierGroupEntity updated = modifierGroupRepository.findById(group.getId()).orElseThrow();
        assertTrue(updated.getUpdatedAt().after(initialUpdatedAt),
                "ModifierGroup updatedAt should be updated when entity changes");
    }

    @Test
    void testModifierEntityUpdatedAtAdvancesOnUpdate() throws InterruptedException {
        ModifierGroupEntity group = modifierGroupRepository.saveAndFlush(ModifierGroupEntity.builder()
                .groupId(UUID.randomUUID().toString())
                .name("Group for Mod")
                .build());

        ModifierEntity mod = modifierRepository.saveAndFlush(ModifierEntity.builder()
                .modifierId(UUID.randomUUID().toString())
                .name("Original Mod")
                .priceAdjustment(BigDecimal.TEN)
                .modifierGroup(group)
                .build());

        entityManager.flush();
        entityManager.clear();

        ModifierEntity loaded = modifierRepository.findById(mod.getId()).orElseThrow();
        Timestamp initialUpdatedAt = loaded.getUpdatedAt();
        assertNotNull(initialUpdatedAt);

        Thread.sleep(100);

        loaded.setName("Updated Mod Name");
        modifierRepository.saveAndFlush(loaded);
        entityManager.clear();

        ModifierEntity updated = modifierRepository.findById(mod.getId()).orElseThrow();
        assertTrue(updated.getUpdatedAt().after(initialUpdatedAt),
                "Modifier updatedAt should be updated when entity changes");
    }
}
