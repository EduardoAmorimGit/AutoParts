package com.autoparts_maven.autoparts_maven.customer;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AddressRepository extends JpaRepository<Address, Long> {

    List<Address> findByUserIdOrderByIdAsc(Long userId);

    Optional<Address> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);

    /** Rows of [district, number of addresses], districts as typed (not normalized). */
    @Query("select a.district, count(a) from Address a group by a.district")
    List<Object[]> countByDistrict();
}
