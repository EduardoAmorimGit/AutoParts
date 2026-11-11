package com.autoparts_maven.autoparts_maven.compatibility;

import com.autoparts_maven.autoparts_maven.compatibility.dto.CompatibilityDtos.CompatibleProductsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Public customer endpoint: the products confirmed for the vehicle found by plate lookup. */
@RestController
@RequiredArgsConstructor
public class CompatibilityController {

    private final CompatibilityService compatibilityService;

    @GetMapping("/api/vehicles/{vehicleId}/products")
    public CompatibleProductsResponse findProductsForVehicle(
            @PathVariable Long vehicleId,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return compatibilityService.findProductsForVehicle(vehicleId, pageable);
    }
}
