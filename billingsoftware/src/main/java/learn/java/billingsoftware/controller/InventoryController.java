package learn.java.billingsoftware.controller;

import learn.java.billingsoftware.io.InventoryTransactionRequest;
import learn.java.billingsoftware.io.InventoryTransactionResponse;
import learn.java.billingsoftware.io.StockCheckRequest;
import learn.java.billingsoftware.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @PostMapping("/admin/inventory/transactions")
    @ResponseStatus(HttpStatus.CREATED)
    public InventoryTransactionResponse recordTransaction(@RequestBody InventoryTransactionRequest request) {
        return inventoryService.recordTransaction(request);
    }

    @PostMapping("/admin/inventory/stock-check")
    @ResponseStatus(HttpStatus.CREATED)
    public InventoryTransactionResponse performStockCheck(@RequestBody StockCheckRequest request) {
        return inventoryService.performStockCheck(request);
    }

    @GetMapping("/admin/inventory/transactions")
    public Page<InventoryTransactionResponse> getAllTransactions(
            @PageableDefault(page = 0, size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return inventoryService.getAllTransactions(pageable);
    }

    @GetMapping("/inventory/variants/{variantId}/transactions")
    public List<InventoryTransactionResponse> getVariantTransactions(@PathVariable("variantId") String variantId) {
        return inventoryService.getTransactionsByVariant(variantId);
    }
}
