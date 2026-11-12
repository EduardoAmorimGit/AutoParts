package com.autoparts_maven.autoparts_maven.delivery;

import com.autoparts_maven.autoparts_maven.delivery.DeliveryDtos.RegionRequest;
import com.autoparts_maven.autoparts_maven.delivery.DeliveryDtos.RegionResponse;
import com.autoparts_maven.autoparts_maven.delivery.DeliveryDtos.UncoveredDistrict;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin-only (/api/admin/**) delivery regions and their fees. */
@RestController
@RequestMapping("/api/admin/delivery-regions")
@RequiredArgsConstructor
public class DeliveryRegionAdminController {

    private final DeliveryRegionService service;

    @GetMapping
    public List<RegionResponse> list() {
        return service.list();
    }

    @GetMapping("/uncovered-districts")
    public List<UncoveredDistrict> uncoveredDistricts() {
        return service.uncoveredDistricts();
    }

    @GetMapping("/{id}")
    public RegionResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<RegionResponse> create(@Valid @RequestBody RegionRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request, adminId(jwt)));
    }

    @PutMapping("/{id}")
    public RegionResponse update(@PathVariable Long id, @Valid @RequestBody RegionRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return service.update(id, request, adminId(jwt));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    private static Long adminId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
