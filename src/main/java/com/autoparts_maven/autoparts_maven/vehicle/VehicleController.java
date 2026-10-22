package com.autoparts_maven.autoparts_maven.vehicle;

import com.autoparts_maven.autoparts_maven.common.PageResponse;
import com.autoparts_maven.autoparts_maven.compatibility.CompatibilityService;
import com.autoparts_maven.autoparts_maven.product.dto.ProductResponse;
import com.autoparts_maven.autoparts_maven.vehicle.dto.VehicleResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vehicles")
@RequiredArgsConstructor
public class VehicleController {

    private final VehicleService vehicleService;
    private final CompatibilityService compatibilityService;

    @PostMapping("/lookup")
    public VehicleResponse lookup(@Valid @RequestBody PlateLookupRequest request) {
        return vehicleService.lookupByPlate(request.plate());
    }

    @GetMapping("/{id}")
    public VehicleResponse findById(@PathVariable Long id) {
        return VehicleResponse.from(vehicleService.getByIdOrThrow(id));
    }

    @GetMapping("/{id}/products")
    public PageResponse<ProductResponse> findProductsForVehicle(
            @PathVariable Long id,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        return compatibilityService.findProductsForVehicle(id, pageable);
    }

    public record PlateLookupRequest(@NotBlank String plate) {
    }
}
