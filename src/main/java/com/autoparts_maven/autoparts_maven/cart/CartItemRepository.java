package com.autoparts_maven.autoparts_maven.cart;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    @EntityGraph(attributePaths = "product")
    List<CartItem> findByUserIdOrderByIdAsc(Long userId);

    /** The user's cart rows, locked: a concurrent checkout of the same cart waits and then finds it empty. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CartItem c where c.userId = :userId order by c.id")
    List<CartItem> findByUserIdForUpdate(Long userId);

    Optional<CartItem> findByUserIdAndProductId(Long userId, Long productId);

    long countByUserId(Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from CartItem c where c.userId = :userId")
    int deleteAllForUser(Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from CartItem c where c.userId = :userId and c.product.id = :productId")
    int deleteItem(Long userId, Long productId);
}
