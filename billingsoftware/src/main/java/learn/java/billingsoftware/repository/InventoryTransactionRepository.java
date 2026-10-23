package learn.java.billingsoftware.repository;

import learn.java.billingsoftware.entity.InventoryTransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryTransactionRepository extends JpaRepository<InventoryTransactionEntity, Long> {

    Optional<InventoryTransactionEntity> findByTransactionId(String transactionId);

    List<InventoryTransactionEntity> findByVariant_VariantIdOrderByCreatedAtDesc(String variantId);

    List<InventoryTransactionEntity> findAllByOrderByCreatedAtDesc();

    @Query("SELECT COALESCE(SUM(t.quantity), 0) FROM InventoryTransactionEntity t WHERE t.variant.variantId = :variantId")
    Integer calculateStockByVariantId(@Param("variantId") String variantId);
}
