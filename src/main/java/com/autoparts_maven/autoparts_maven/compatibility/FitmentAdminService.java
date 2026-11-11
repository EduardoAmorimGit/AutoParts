package com.autoparts_maven.autoparts_maven.compatibility;

import com.autoparts_maven.autoparts_maven.common.PageResponse;
import com.autoparts_maven.autoparts_maven.compatibility.CompatibilityExceptions.DuplicateSpecException;
import com.autoparts_maven.autoparts_maven.compatibility.CompatibilityExceptions.FitmentNotFoundException;
import com.autoparts_maven.autoparts_maven.compatibility.CompatibilityExceptions.SpecNotFoundException;
import com.autoparts_maven.autoparts_maven.compatibility.dto.CompatibilityDtos.FitmentRequest;
import com.autoparts_maven.autoparts_maven.compatibility.dto.CompatibilityDtos.FitmentResponse;
import com.autoparts_maven.autoparts_maven.compatibility.dto.CompatibilityDtos.PendingVehicleResponse;
import com.autoparts_maven.autoparts_maven.compatibility.dto.CompatibilityDtos.ReviewRequest;
import com.autoparts_maven.autoparts_maven.compatibility.dto.CompatibilityDtos.VehicleSpecRequest;
import com.autoparts_maven.autoparts_maven.compatibility.dto.CompatibilityDtos.VehicleSpecResponse;
import com.autoparts_maven.autoparts_maven.product.Product;
import com.autoparts_maven.autoparts_maven.product.ProductNotFoundException;
import com.autoparts_maven.autoparts_maven.product.ProductRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Administration of specs ("fichas"), fitments and the queue of vehicles without a spec. */
@Service
@RequiredArgsConstructor
public class FitmentAdminService {

    private final VehicleSpecRepository specRepository;
    private final ProductFitmentRepository fitmentRepository;
    private final ProductRepository productRepository;
    private final VehicleSpecAssignmentRepository assignmentRepository;

    @Transactional(readOnly = true)
    public PageResponse<VehicleSpecResponse> searchSpecs(String brand, String model, Pageable pageable) {
        String normalizedBrand = VehicleNames.brand(brand);
        String normalizedModel = VehicleNames.model(model, normalizedBrand);
        return PageResponse.from(specRepository.search(normalizedBrand, normalizedModel, pageable)
                .map(VehicleSpecResponse::from));
    }

    @Transactional
    public VehicleSpecResponse createSpec(VehicleSpecRequest request) {
        SpecDraft draft = draftOf(request);
        if (findIdentical(draft).isPresent()) {
            throw new DuplicateSpecException();
        }
        return VehicleSpecResponse.from(saveSpec(draft.toEntity()));
    }

    @Transactional
    public VehicleSpecResponse updateSpec(Long id, VehicleSpecRequest request) {
        VehicleSpec spec = getSpec(id);
        SpecDraft draft = draftOf(request);
        findIdentical(draft).filter(other -> !other.getId().equals(id)).ifPresent(other -> {
            throw new DuplicateSpecException();
        });
        draft.applyTo(spec);
        return VehicleSpecResponse.from(saveSpec(spec));
    }

    /** Deleting a spec also deletes its fitments and vehicle assignments (database cascade). */
    @Transactional
    public void deleteSpec(Long id) {
        specRepository.delete(getSpec(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<FitmentResponse> searchFitments(FitmentStatus status, Long productId, Long specId,
            Pageable pageable) {
        return PageResponse.from(fitmentRepository.search(status, productId, specId, pageable)
                .map(FitmentResponse::from));
    }

    /** A fitment entered by hand is validated at once (the admin is the reviewer). */
    @Transactional
    public FitmentResponse createFitment(FitmentRequest request, Long adminId) {
        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new ProductNotFoundException(request.productId()));
        VehicleSpec spec = getSpec(request.specId());
        ProductFitment fitment = fitmentRepository.findByProductIdAndSpecId(product.getId(), spec.getId())
                .orElseGet(ProductFitment::new);
        fitment.setProduct(product);
        fitment.setSpec(spec);
        fitment.setSource(request.source().trim());
        fitment.setNotes(request.notes());
        fitment.review(FitmentStatus.VALIDATED, adminId, Instant.now());
        return FitmentResponse.from(fitmentRepository.save(fitment));
    }

    @Transactional
    public int review(ReviewRequest request, Long adminId) {
        List<ProductFitment> fitments = fitmentRepository.findByIdIn(request.ids());
        if (fitments.size() != request.ids().stream().distinct().count()) {
            Long missing = request.ids().stream()
                    .filter(id -> fitments.stream().noneMatch(f -> f.getId().equals(id)))
                    .findFirst().orElseThrow();
            throw new FitmentNotFoundException(missing);
        }
        Instant now = Instant.now();
        fitments.forEach(fitment -> fitment.review(request.decision(), adminId, now));
        return fitments.size();
    }

    @Transactional
    public void deleteFitment(Long id) {
        fitmentRepository.delete(fitmentRepository.findById(id).orElseThrow(() -> new FitmentNotFoundException(id)));
    }

    @Transactional(readOnly = true)
    public PageResponse<PendingVehicleResponse> pendingVehicles(Pageable pageable) {
        return PageResponse.from(assignmentRepository.findUnassignedVehicles(pageable)
                .map(v -> new PendingVehicleResponse(v.getId(), v.getBrand(), v.getModel(), v.getVersion(),
                        v.getManufactureYear(), v.getModelYear(), v.getEngineCc(), v.getFuel(), v.getCreatedAt())));
    }

    Optional<VehicleSpec> findIdentical(SpecDraft draft) {
        return specRepository.findIdentical(draft.brand(), draft.model(), draft.version(), draft.engine(),
                draft.fuelName(), draft.yearStart(), draft.yearEnd());
    }

    private VehicleSpec saveSpec(VehicleSpec spec) {
        try {
            return specRepository.saveAndFlush(spec);
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateSpecException();
        }
    }

    private VehicleSpec getSpec(Long id) {
        return specRepository.findById(id).orElseThrow(() -> new SpecNotFoundException(id));
    }

    private static SpecDraft draftOf(VehicleSpecRequest request) {
        return SpecDraft.of(request.brand(), request.model(), request.version(), request.engine(), request.fuel(),
                request.yearStart(), request.yearEnd(), request.oilCapacityLiters());
    }
}
