package com.autoparts_maven.autoparts_maven.delivery;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface DeliveryRegionRepository extends JpaRepository<DeliveryRegion, Long> {

    @Query("select distinct r from DeliveryRegion r left join fetch r.districts order by r.name")
    List<DeliveryRegion> findAllWithDistricts();

    @Query("select r from DeliveryRegion r join r.districts d where d.districtKey = :key")
    Optional<DeliveryRegion> findByDistrictKey(String key);

    /** Districts among {@code keys} that already belong to a region other than {@code regionId}. */
    @Query("""
            select new com.autoparts_maven.autoparts_maven.delivery.DistrictOwner(d.name, r.name)
            from DeliveryRegion r join r.districts d
            where d.districtKey in :keys and r.id <> :regionId order by d.name""")
    List<DistrictOwner> findOwners(Collection<String> keys, Long regionId);

    @Query("select d.districtKey from DeliveryRegion r join r.districts d where r.active = true and r.fee is not null")
    List<String> findServedKeys();

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
