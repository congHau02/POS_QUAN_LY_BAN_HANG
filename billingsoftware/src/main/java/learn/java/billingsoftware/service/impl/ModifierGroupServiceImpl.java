package learn.java.billingsoftware.service.impl;

import learn.java.billingsoftware.entity.ItemEntity;
import learn.java.billingsoftware.entity.ModifierEntity;
import learn.java.billingsoftware.entity.ModifierGroupEntity;
import learn.java.billingsoftware.io.ModifierGroupRequest;
import learn.java.billingsoftware.io.ModifierGroupResponse;
import learn.java.billingsoftware.io.ModifierRequest;
import learn.java.billingsoftware.io.ModifierResponse;
import learn.java.billingsoftware.repository.ItemRepository;
import learn.java.billingsoftware.repository.ModifierGroupRepository;
import learn.java.billingsoftware.service.ActivityLogService;
import learn.java.billingsoftware.service.ModifierGroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ModifierGroupServiceImpl implements ModifierGroupService {

    private final ModifierGroupRepository modifierGroupRepository;
    private final ItemRepository itemRepository;
    private final ActivityLogService activityLogService;

    @Override
    @Transactional
    public ModifierGroupResponse create(ModifierGroupRequest request) {
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Modifier group name is required");
        }

        ModifierGroupEntity groupEntity = ModifierGroupEntity.builder()
                .groupId(UUID.randomUUID().toString())
                .name(request.getName().trim())
                .description(request.getDescription())
                .minSelections(request.getMinSelections() != null ? request.getMinSelections() : 0)
                .maxSelections(request.getMaxSelections() != null ? request.getMaxSelections() : 1)
                .modifiers(new ArrayList<>())
                .build();

        if (request.getModifiers() != null) {
            for (ModifierRequest modReq : request.getModifiers()) {
                ModifierEntity mod = buildModifierEntity(modReq, groupEntity);
                if (mod != null) {
                    groupEntity.getModifiers().add(mod);
                }
            }
        }

        validateModifierGroup(groupEntity);

        ModifierGroupEntity saved = modifierGroupRepository.save(groupEntity);
        activityLogService.logActivity("CREATE", "MODIFIER_GROUP", saved.getGroupId(), "Created modifier group: " + saved.getName());
        return convertToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ModifierGroupResponse> fetchAll() {
        return modifierGroupRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ModifierGroupResponse fetchById(String groupId) {
        ModifierGroupEntity entity = modifierGroupRepository.findByGroupId(groupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Modifier group not found: " + groupId));
        return convertToResponse(entity);
    }

    @Override
    @Transactional
    public ModifierGroupResponse update(String groupId, ModifierGroupRequest request) {
        ModifierGroupEntity entity = modifierGroupRepository.findByGroupId(groupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Modifier group not found: " + groupId));

        if (request.getName() != null && !request.getName().trim().isEmpty()) {
            entity.setName(request.getName().trim());
        }
        entity.setDescription(request.getDescription());
        if (request.getMinSelections() != null) {
            entity.setMinSelections(request.getMinSelections());
        }
        if (request.getMaxSelections() != null) {
            entity.setMaxSelections(request.getMaxSelections());
        }

        if (request.getModifiers() != null) {
            entity.getModifiers().clear();
            for (ModifierRequest modReq : request.getModifiers()) {
                ModifierEntity mod = buildModifierEntity(modReq, entity);
                if (mod != null) {
                    entity.getModifiers().add(mod);
                }
            }
        }

        validateModifierGroup(entity);

        ModifierGroupEntity saved = modifierGroupRepository.save(entity);
        activityLogService.logActivity("UPDATE", "MODIFIER_GROUP", saved.getGroupId(), "Updated modifier group: " + saved.getName());
        return convertToResponse(saved);
    }

    private void validateModifierGroup(ModifierGroupEntity entity) {
        long defaultCount = entity.getModifiers().stream()
                .filter(m -> Boolean.TRUE.equals(m.getIsDefault()))
                .count();
        if (entity.getMaxSelections() != null && entity.getMaxSelections() > 0 && defaultCount > entity.getMaxSelections()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Số lượng tùy chọn mặc định (" + defaultCount + ") vượt quá số lượng tối đa cho phép (" + entity.getMaxSelections() + ")");
        }
    }

    private ModifierEntity buildModifierEntity(ModifierRequest modReq, ModifierGroupEntity parent) {
        if (modReq.getName() == null || modReq.getName().trim().isEmpty()) {
            return null;
        }
        return ModifierEntity.builder()
                .modifierId(UUID.randomUUID().toString())
                .name(modReq.getName().trim())
                .priceAdjustment(modReq.getPriceAdjustment() != null ? modReq.getPriceAdjustment() : BigDecimal.ZERO)
                .isDefault(Boolean.TRUE.equals(modReq.getIsDefault()))
                .modifierGroup(parent)
                .build();
    }

    @Override
    @Transactional
    public void delete(String groupId) {
        ModifierGroupEntity entity = modifierGroupRepository.findByGroupId(groupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Modifier group not found: " + groupId));

        // Unlink from any items
        for (ItemEntity item : new ArrayList<>(entity.getItems())) {
            item.getModifierGroups().remove(entity);
            itemRepository.save(item);
        }
        entity.getItems().clear();

        modifierGroupRepository.delete(entity);
        activityLogService.logActivity("DELETE", "MODIFIER_GROUP", entity.getGroupId(), "Deleted modifier group: " + entity.getName());
    }

    @Override
    @Transactional
    public void attachToItem(String itemId, List<String> groupIds) {
        ItemEntity item = itemRepository.findByItemId(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found: " + itemId));

        if (groupIds == null || groupIds.isEmpty()) {
            item.getModifierGroups().clear();
        } else {
            List<ModifierGroupEntity> groups = modifierGroupRepository.findByGroupIdIn(groupIds);
            item.setModifierGroups(groups);
        }
        itemRepository.save(item);
        activityLogService.logActivity("UPDATE", "MODIFIER_GROUP", itemId, "Attached modifier groups to item " + itemId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ModifierGroupResponse> fetchByItemId(String itemId) {
        ItemEntity item = itemRepository.findByItemId(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found: " + itemId));

        return item.getModifierGroups()
                .stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    public ModifierGroupResponse convertToResponse(ModifierGroupEntity entity) {
        List<ModifierResponse> modifierResponses = entity.getModifiers() != null
                ? entity.getModifiers().stream()
                .map(m -> ModifierResponse.builder()
                        .modifierId(m.getModifierId())
                        .name(m.getName())
                        .priceAdjustment(m.getPriceAdjustment())
                        .isDefault(Boolean.TRUE.equals(m.getIsDefault()))
                        .build())
                .collect(Collectors.toList())
                : new ArrayList<>();

        return ModifierGroupResponse.builder()
                .groupId(entity.getGroupId())
                .name(entity.getName())
                .description(entity.getDescription())
                .minSelections(entity.getMinSelections())
                .maxSelections(entity.getMaxSelections())
                .modifiers(modifierResponses)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
