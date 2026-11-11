package com.autoparts_maven.autoparts_maven.compatibility;

import com.autoparts_maven.autoparts_maven.common.PageResponse;
import com.autoparts_maven.autoparts_maven.compatibility.CompatibilityExceptions.InvalidImportFileException;
import com.autoparts_maven.autoparts_maven.compatibility.dto.CompatibilityDtos.AssignSpecRequest;
import com.autoparts_maven.autoparts_maven.compatibility.dto.CompatibilityDtos.FitmentRequest;
import com.autoparts_maven.autoparts_maven.compatibility.dto.CompatibilityDtos.FitmentResponse;
import com.autoparts_maven.autoparts_maven.compatibility.dto.CompatibilityDtos.ImportReport;
import com.autoparts_maven.autoparts_maven.compatibility.dto.CompatibilityDtos.PendingVehicleResponse;
import com.autoparts_maven.autoparts_maven.compatibility.dto.CompatibilityDtos.RematchResult;
import com.autoparts_maven.autoparts_maven.compatibility.dto.CompatibilityDtos.ReviewRequest;
import com.autoparts_maven.autoparts_maven.compatibility.dto.CompatibilityDtos.ReviewResult;
import com.autoparts_maven.autoparts_maven.compatibility.dto.CompatibilityDtos.VehicleSpecRequest;
import com.autoparts_maven.autoparts_maven.compatibility.dto.CompatibilityDtos.VehicleSpecResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Admin-only (see SecurityConfig: /api/admin/**) management of specs, fitments and pending vehicles. */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class CompatibilityAdminController {

    private final FitmentAdminService fitmentAdminService;
    private final FitmentImportService fitmentImportService;
    private final SpecResolutionService specResolutionService;

    @GetMapping("/vehicle-specs")
    public PageResponse<VehicleSpecResponse> searchSpecs(
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String model,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        return fitmentAdminService.searchSpecs(brand, model, pageable);
    }

    @PostMapping("/vehicle-specs")
    public ResponseEntity<VehicleSpecResponse> createSpec(@Valid @RequestBody VehicleSpecRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(fitmentAdminService.createSpec(request));
    }

    @PutMapping("/vehicle-specs/{id}")
    public VehicleSpecResponse updateSpec(@PathVariable Long id, @Valid @RequestBody VehicleSpecRequest request) {
        return fitmentAdminService.updateSpec(id, request);
    }

    @DeleteMapping("/vehicle-specs/{id}")
    public ResponseEntity<Void> deleteSpec(@PathVariable Long id) {
        fitmentAdminService.deleteSpec(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/fitments")
    public PageResponse<FitmentResponse> searchFitments(
            @RequestParam(required = false) FitmentStatus status,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Long specId,
            @PageableDefault(size = 50, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        return fitmentAdminService.searchFitments(status, productId, specId, pageable);
    }

    @PostMapping("/fitments")
    public ResponseEntity<FitmentResponse> createFitment(@Valid @RequestBody FitmentRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED).body(fitmentAdminService.createFitment(request, adminId(jwt)));
    }

    @PostMapping("/fitments/review")
    public ReviewResult review(@Valid @RequestBody ReviewRequest request, @AuthenticationPrincipal Jwt jwt) {
        return new ReviewResult(fitmentAdminService.review(request, adminId(jwt)));
    }

    @DeleteMapping("/fitments/{id}")
    public ResponseEntity<Void> deleteFitment(@PathVariable Long id) {
        fitmentAdminService.deleteFitment(id);
        return ResponseEntity.noContent().build();
    }

    /** Multipart upload of a CSV; {@code dryRun=true} validates and reports without saving. */
    @PostMapping(value = "/fitments/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportReport importFitments(@RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean dryRun) {
        if (file.isEmpty()) {
            throw new InvalidImportFileException("The uploaded file is empty");
        }
        try (InputStream input = file.getInputStream()) {
            return fitmentImportService.importCsv(input, dryRun);
        } catch (IOException exception) {
            throw new InvalidImportFileException("Could not read the uploaded file");
        }
    }

    @GetMapping("/vehicles/pending")
    public PageResponse<PendingVehicleResponse> pendingVehicles(
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return fitmentAdminService.pendingVehicles(pageable);
    }

    @PutMapping("/vehicles/{vehicleId}/spec")
    public ResponseEntity<Void> assignSpec(@PathVariable Long vehicleId, @Valid @RequestBody AssignSpecRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        specResolutionService.assignManually(vehicleId, request.specId(), adminId(jwt));
        return ResponseEntity.noContent().build();
    }

    /** Re-runs automatic resolution, e.g. after importing new specs. */
    @PostMapping("/vehicles/rematch")
    public RematchResult rematch() {
        return new RematchResult(specResolutionService.rematch());
    }

    private static Long adminId(Jwt jwt) {
        return jwt == null ? null : Long.valueOf(jwt.getSubject());
    }
}
