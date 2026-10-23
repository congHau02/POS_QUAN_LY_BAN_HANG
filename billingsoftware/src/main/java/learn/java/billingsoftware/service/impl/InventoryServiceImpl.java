package learn.java.billingsoftware.service.impl;

import learn.java.billingsoftware.entity.InventoryTransactionEntity;
import learn.java.billingsoftware.entity.TransactionType;
import learn.java.billingsoftware.entity.VariantEntity;
import learn.java.billingsoftware.io.InventoryTransactionRequest;
import learn.java.billingsoftware.io.InventoryTransactionResponse;
import learn.java.billingsoftware.io.StockCheckRequest;
import learn.java.billingsoftware.repository.InventoryTransactionRepository;
import learn.java.billingsoftware.repository.VariantRepository;
import learn.java.billingsoftware.service.ActivityLogService;
import learn.java.billingsoftware.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final VariantRepository variantRepository;
    private final ActivityLogService activityLogService;

    @Override
    @Transactional
    public InventoryTransactionResponse recordTransaction(InventoryTransactionRequest request) {
        if (request.getVariantId() == null || request.getVariantId().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Variant ID is required");
        }

        if (request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity must be greater than 0");
        }

        if (request.getTransactionType() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Transaction type is required");
        }

        VariantEntity variant = variantRepository.findByVariantId(request.getVariantId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Variant not found with ID: " + request.getVariantId()));

        int signedQuantity;
        if (request.getTransactionType() == TransactionType.IN) {
            signedQuantity = Math.abs(request.getQuantity());
        } else if (request.getTransactionType() == TransactionType.OUT) {
            signedQuantity = -Math.abs(request.getQuantity());
        } else {
            signedQuantity = request.getQuantity();
        }

        InventoryTransactionEntity transaction = InventoryTransactionEntity.builder()
                .transactionId(UUID.randomUUID().toString())
                .variant(variant)
                .transactionType(request.getTransactionType())
                .quantity(signedQuantity)
                .referenceId(request.getReferenceId() != null ? request.getReferenceId().trim() : null)
                .note(request.getNote() != null ? request.getNote().trim() : null)
                .build();

        InventoryTransactionEntity savedTx = inventoryTransactionRepository.saveAndFlush(transaction);

        // Recalculate cached stock from the sum of ledger transactions
        Integer calculatedStock = inventoryTransactionRepository.calculateStockByVariantId(variant.getVariantId());
        variant.setCachedStockQuantity(calculatedStock != null ? calculatedStock : 0);
        variantRepository.save(variant);

        activityLogService.logActivity("CREATE", "INVENTORY", transaction.getTransactionId(), "Recorded inventory transaction " + transaction.getTransactionType() + " for variant " + variant.getSku());

        return convertToResponse(savedTx, variant.getCachedStockQuantity());
    }

    @Override
    @Transactional
    public InventoryTransactionResponse performStockCheck(StockCheckRequest request) {
        if (request.getVariantId() == null || request.getVariantId().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Variant ID is required");
        }

        if (request.getActualCount() == null || request.getActualCount() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Actual count must be greater than or equal to 0");
        }

        VariantEntity variant = variantRepository.findByVariantId(request.getVariantId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Variant not found with ID: " + request.getVariantId()));

        Integer ledgerStock = inventoryTransactionRepository.calculateStockByVariantId(variant.getVariantId());
        int currentLedgerStock = ledgerStock != null ? ledgerStock : 0;
        int discrepancy = request.getActualCount() - currentLedgerStock;

        String refId = (request.getReferenceId() != null && !request.getReferenceId().trim().isEmpty())
                ? request.getReferenceId().trim()
                : "CHECK-" + System.currentTimeMillis();

        String note = (request.getNote() != null && !request.getNote().trim().isEmpty())
                ? request.getNote().trim()
                : (discrepancy == 0 ? "Periodic stock check (Exact Match)" : "Periodic stock check adjustment");

        InventoryTransactionEntity transaction = InventoryTransactionEntity.builder()
                .transactionId(UUID.randomUUID().toString())
                .variant(variant)
                .transactionType(TransactionType.ADJUSTMENT)
                .quantity(discrepancy)
                .referenceId(refId)
                .note(note)
                .build();

        InventoryTransactionEntity savedTx = inventoryTransactionRepository.saveAndFlush(transaction);

        // Recalculate cached stock from the sum of ledger transactions
        Integer calculatedStock = inventoryTransactionRepository.calculateStockByVariantId(variant.getVariantId());
        variant.setCachedStockQuantity(calculatedStock != null ? calculatedStock : request.getActualCount());
        variantRepository.save(variant);

        activityLogService.logActivity("UPDATE", "INVENTORY", transaction.getTransactionId(), "Performed stock check for variant " + variant.getSku() + " (Actual: " + request.getActualCount() + ")");

        return convertToResponse(savedTx, variant.getCachedStockQuantity());
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventoryTransactionResponse> getTransactionsByVariant(String variantId) {
        if (variantId == null || variantId.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Variant ID is required");
        }

        return inventoryTransactionRepository.findByVariant_VariantIdOrderByCreatedAtDesc(variantId)
                .stream()
                .map(tx -> convertToResponse(tx, null))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventoryTransactionResponse> getAllTransactions() {
        return inventoryTransactionRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(tx -> convertToResponse(tx, null))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<InventoryTransactionResponse> getAllTransactions(Pageable pageable) {
        return inventoryTransactionRepository.findAll(pageable)
                .map(tx -> convertToResponse(tx, null));
    }

    private InventoryTransactionResponse convertToResponse(InventoryTransactionEntity entity, Integer balanceAfter) {
        VariantEntity variant = entity.getVariant();
        String sku = variant != null ? variant.getSku() : null;
        String itemName = (variant != null && variant.getItem() != null) ? variant.getItem().getName() : null;
        String variantId = variant != null ? variant.getVariantId() : null;

        return InventoryTransactionResponse.builder()
                .transactionId(entity.getTransactionId())
                .variantId(variantId)
                .sku(sku)
                .itemName(itemName)
                .transactionType(entity.getTransactionType())
                .quantity(entity.getQuantity())
                .referenceId(entity.getReferenceId())
                .note(entity.getNote())
                .balanceAfter(balanceAfter)
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
