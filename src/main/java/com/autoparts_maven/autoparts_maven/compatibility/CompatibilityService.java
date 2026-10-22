package com.autoparts_maven.autoparts_maven.compatibility;

import com.autoparts_maven.autoparts_maven.common.PageResponse;
import com.autoparts_maven.autoparts_maven.product.Product;
import com.autoparts_maven.autoparts_maven.product.ProductNotFoundException;
import com.autoparts_maven.autoparts_maven.product.ProductRepository;
import com.autoparts_maven.autoparts_maven.product.dto.ProductResponse;
import com.autoparts_maven.autoparts_maven.vehicle.Vehicle;
import com.autoparts_maven.autoparts_maven.vehicle.VehicleNotFoundException;
import com.autoparts_maven.autoparts_maven.vehicle.VehicleRepository;
import com.autoparts_maven.autoparts_maven.vehicle.dto.VehicleResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CompatibilityService {

    private final ProductRepository productRepository;
    private final VehicleRepository vehicleRepository;
    private final ProductVehicleRepository productVehicleRepository;

    @Transactional
    public void link(Long productId, Long vehicleId) {
        if (productVehicleRepository.findByProductAndVehicle(
                getProductOrThrow(productId), getVehicleOrThrow(vehicleId)).isPresent()) {
            return;
        }
        productVehicleRepository.save(new ProductVehicle(getProductOrThrow(productId), getVehicleOrThrow(vehicleId)));
    }

    @Transactional
    public void unlink(Long productId, Long vehicleId) {
        productVehicleRepository.deleteByProductAndVehicle(
                getProductOrThrow(productId), getVehicleOrThrow(vehicleId));
    }

    @Transactional(readOnly = true)
    public PageResponse<VehicleResponse> findVehiclesForProduct(Long productId, Pageable pageable) {
        Product product = getProductOrThrow(productId);
        return PageResponse.from(
                productVehicleRepository.findByProduct(product, pageable).map(l -> VehicleResponse.from(l.getVehicle())));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> findProductsForVehicle(Long vehicleId, Pageable pageable) {
        Vehicle vehicle = getVehicleOrThrow(vehicleId);
        return PageResponse.from(
                productVehicleRepository.findByVehicle(vehicle, pageable).map(l -> ProductResponse.from(l.getProduct())));
    }

    private Product getProductOrThrow(Long id) {
        return productRepository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
    }

    private Vehicle getVehicleOrThrow(Long id) {
        return vehicleRepository.findById(id).orElseThrow(() -> new VehicleNotFoundException(id));
    }
}
