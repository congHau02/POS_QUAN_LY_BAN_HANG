package learn.java.billingsoftware.repository;

import learn.java.billingsoftware.entity.ItemEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ItemRepository extends JpaRepository<ItemEntity, Long> {

    @Override
    @EntityGraph(attributePaths = {"category", "variants"})
    List<ItemEntity> findAll();

    @EntityGraph(attributePaths = {"category", "variants"})
    Optional<ItemEntity> findByItemId(String id);

    Integer countByCategoryId(Long id);
}
