package com.autoparts_maven.autoparts_maven.delivery;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A district (bairro) of a delivery region, as typed by the admin, plus its comparison key. */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegionDistrict {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "district_key", nullable = false, length = 100)
    private String districtKey;

    RegionDistrict(String name) {
        this.name = name.trim().replaceAll("\\s+", " ");
        this.districtKey = DistrictKey.of(name);
    }
}
