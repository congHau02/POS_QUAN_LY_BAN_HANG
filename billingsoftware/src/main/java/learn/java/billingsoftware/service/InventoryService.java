package learn.java.billingsoftware.service;

import learn.java.billingsoftware.io.InventoryTransactionRequest;
import learn.java.billingsoftware.io.InventoryTransactionResponse;
import learn.java.billingsoftware.io.StockCheckRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface InventoryService {

    InventoryTransactionResponse recordTransaction(InventoryTransactionRequest request);

    InventoryTransactionResponse performStockCheck(StockCheckRequest request);

    List<InventoryTransactionResponse> getTransactionsByVariant(String variantId);

    List<InventoryTransactionResponse> getAllTransactions();

    Page<InventoryTransactionResponse> getAllTransactions(Pageable pageable);
}
