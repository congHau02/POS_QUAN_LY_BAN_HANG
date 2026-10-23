package learn.java.billingsoftware.repository;

import learn.java.billingsoftware.entity.CustomerEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<CustomerEntity, Long> {

    Optional<CustomerEntity> findByCustomerId(String customerId);

    Optional<CustomerEntity> findByPhoneNumber(String phoneNumber);

    boolean existsByPhoneNumber(String phoneNumber);

    List<CustomerEntity> findAllByOrderByCreatedAtDesc();

    @Query("SELECT c FROM CustomerEntity c WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :query, '%')) OR c.phoneNumber LIKE CONCAT('%', :query, '%') ORDER BY c.createdAt DESC")
    List<CustomerEntity> searchByNameOrPhoneNumber(@Param("query") String query);

    @Query(value = "SELECT c FROM CustomerEntity c WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :query, '%')) OR c.phoneNumber LIKE CONCAT('%', :query, '%')",
           countQuery = "SELECT COUNT(c) FROM CustomerEntity c WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :query, '%')) OR c.phoneNumber LIKE CONCAT('%', :query, '%')")
    Page<CustomerEntity> searchByNameOrPhoneNumber(@Param("query") String query, Pageable pageable);
}
