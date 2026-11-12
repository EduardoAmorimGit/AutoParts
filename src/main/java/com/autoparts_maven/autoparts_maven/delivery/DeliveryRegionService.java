package com.autoparts_maven.autoparts_maven.delivery;

import com.autoparts_maven.autoparts_maven.customer.AddressRepository;
import com.autoparts_maven.autoparts_maven.delivery.DeliveryDtos.DeliveryQuote;
import com.autoparts_maven.autoparts_maven.delivery.DeliveryDtos.RegionRequest;
import com.autoparts_maven.autoparts_maven.delivery.DeliveryDtos.RegionResponse;
import com.autoparts_maven.autoparts_maven.delivery.DeliveryDtos.UncoveredDistrict;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Delivery fees by region. The district (bairro) of the customer's address picks the region; a
 * district in no active region is not served.
 */
@Service
@RequiredArgsConstructor
public class DeliveryRegionService {

    private static final long NO_REGION = -1L;

    private final DeliveryRegionRepository repository;
    private final AddressRepository addressRepository;

    /** The fee for a district, or empty when no active region with a fee serves it. */
    @Transactional(readOnly = true)
    public Optional<DeliveryQuote> quote(String district) {
        String key = DistrictKey.of(district);
        if (key.isEmpty()) {
            return Optional.empty();
        }
        return repository.findByDistrictKey(key).filter(region -> region.isActive() && region.getFee() != null)
                .map(region -> new DeliveryQuote(region.getId(), region.getName(), region.getFee()));
    }

    @Transactional(readOnly = true)
    public List<RegionResponse> list() {
        return repository.findAllWithDistricts().stream().map(RegionResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public RegionResponse get(Long id) {
        return RegionResponse.from(find(id));
    }

    @Transactional
    public RegionResponse create(RegionRequest request, Long adminId) {
        DeliveryRegion region = new DeliveryRegion();
        apply(region, request, adminId, NO_REGION);
        return RegionResponse.from(save(region));
    }

    @Transactional
    public RegionResponse update(Long id, RegionRequest request, Long adminId) {
        DeliveryRegion region = find(id);
        apply(region, request, adminId, id);
        return RegionResponse.from(save(region));
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(find(id));
    }

    /**
     * Districts where customers already saved addresses but that no active region serves, most
     * frequent first: the admins' to-do list for new regions.
     */
    @Transactional(readOnly = true)
    public List<UncoveredDistrict> uncoveredDistricts() {
        Set<String> served = new HashSet<>(repository.findServedKeys());
        Map<String, UncoveredDistrict> byKey = new LinkedHashMap<>();
        for (Object[] row : addressRepository.countByDistrict()) {
            String district = (String) row[0];
            long count = ((Number) row[1]).longValue();
            String key = DistrictKey.of(district);
            if (served.contains(key)) {
                continue;
            }
            byKey.merge(key, new UncoveredDistrict(district.trim(), count),
                    (a, b) -> new UncoveredDistrict(a.district(), a.addresses() + b.addresses()));
        }
        return byKey.values().stream()
                .sorted(Comparator.comparingLong(UncoveredDistrict::addresses).reversed()
                        .thenComparing(UncoveredDistrict::district))
                .toList();
    }

    private void apply(DeliveryRegion region, RegionRequest request, Long adminId, Long regionId) {
        String name = request.name().trim().replaceAll("\\s+", " ");
        if (repository.existsByNameIgnoreCaseAndIdNot(name, regionId)) {
            throw new DuplicateRegionNameException(name);
        }
        Map<String, RegionDistrict> districts = new LinkedHashMap<>();
        for (String district : request.districts()) {
            RegionDistrict parsed = new RegionDistrict(district);
            districts.putIfAbsent(parsed.getDistrictKey(), parsed);
        }
        List<DistrictOwner> taken = repository.findOwners(districts.keySet(), regionId);
        if (!taken.isEmpty()) {
            throw new DistrictAlreadyAssignedException(taken);
        }
        region.setName(name);
        region.setFee(request.fee());
        region.setActive(request.active());
        region.setUpdatedBy(adminId);
        region.getDistricts().clear();
        region.getDistricts().addAll(districts.values());
    }

    private DeliveryRegion save(DeliveryRegion region) {
        try {
            return repository.saveAndFlush(region);
        } catch (DataIntegrityViolationException exception) {
            throw new ConcurrentRegionEditException(exception);
        }
    }

    private DeliveryRegion find(Long id) {
        return repository.findById(id).orElseThrow(() -> new DeliveryRegionNotFoundException(id));
    }
}
