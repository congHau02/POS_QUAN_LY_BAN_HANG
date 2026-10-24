package learn.java.billingsoftware.repository;

import learn.java.billingsoftware.entity.PromotionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PromotionRepository extends JpaRepository<PromotionEntity, Long> {

    Optional<PromotionEntity> findByPromotionId(String promotionId);

    Optional<PromotionEntity> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    List<PromotionEntity> findAllByOrderByCreatedAtDesc();

    List<PromotionEntity> findByIsActiveTrueOrderByCreatedAtDesc();

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE PromotionEntity p SET p.timesUsed = p.timesUsed + 1 WHERE p.promotionId = :id AND (p.usageLimit IS NULL OR p.timesUsed < p.usageLimit)")
    int incrementTimesUsedIfUnderLimit(@Param("id") String promotionId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE PromotionEntity p SET p.timesUsed = p.timesUsed - 1 WHERE p.promotionId = :id AND p.timesUsed > 0")
    int decrementTimesUsed(@Param("id") String promotionId);
}
