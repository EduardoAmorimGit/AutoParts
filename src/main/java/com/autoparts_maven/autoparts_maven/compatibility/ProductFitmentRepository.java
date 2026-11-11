package com.autoparts_maven.autoparts_maven.compatibility;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProductFitmentRepository extends JpaRepository<ProductFitment, Long> {

    Optional<ProductFitment> findByProductIdAndSpecId(Long productId, Long specId);

    @EntityGraph(attributePaths = {"product", "spec"})
    @Query("""
            select f from ProductFitment f
            where (:status is null or f.status = :status)
              and (:productId is null or f.product.id = :productId)
              and (:specId is null or f.spec.id = :specId)
            """)
    Page<ProductFitment> search(FitmentStatus status, Long productId, Long specId, Pageable pageable);

    List<ProductFitment> findByIdIn(Collection<Long> ids);
}
