package com.autoparts_maven.autoparts_maven.product;

import com.autoparts_maven.autoparts_maven.common.PageResponse;
import com.autoparts_maven.autoparts_maven.product.dto.ProductRequest;
import com.autoparts_maven.autoparts_maven.product.dto.ProductResponse;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private static final String SKU_UNIQUE_CONSTRAINT = "uq_products_sku";

    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> findAll(Pageable pageable) {
        return PageResponse.from(productRepository.findAll(pageable).map(ProductResponse::from));
    }

    @Transactional(readOnly = true)
    public ProductResponse findById(Long id) {
        return ProductResponse.from(getProductOrThrow(id));
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        String sku = normalizeSku(request.sku());
        if (productRepository.existsBySku(sku)) {
            throw new DuplicateSkuException(sku);
        }

        Product product = new Product();
        applyRequest(product, request, sku);
        product.setActive(request.active() == null || request.active());

        return ProductResponse.from(saveAndFlush(product, sku));
    }

    /**
     * Full update of a product. {@code expectedVersion} (from an {@code If-Match} header) is optional;
     * when present it must match the stored version so a stale client cannot overwrite newer data.
     * An omitted {@code active} keeps the current status instead of reactivating the product.
     */
    @Transactional
    public ProductResponse update(Long id, ProductRequest request, Long expectedVersion) {
        Product product = getProductOrThrow(id);
        if (expectedVersion != null && !expectedVersion.equals(product.getVersion())) {
            throw new ProductVersionMismatchException(id);
        }

        String sku = normalizeSku(request.sku());
        if (productRepository.existsBySkuAndIdNot(sku, id)) {
            throw new DuplicateSkuException(sku);
        }

        applyRequest(product, request, sku);
        if (request.active() != null) {
            product.setActive(request.active());
        }

        return ProductResponse.from(saveAndFlush(product, sku));
    }

    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        return update(id, request, null);
    }

    @Transactional
    public void delete(Long id) {
        productRepository.delete(getProductOrThrow(id));
    }

    private Product getProductOrThrow(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    // Flushing inside the transaction surfaces a concurrent duplicate SKU (unique constraint)
    // here, where it can be reported as 409 instead of escaping as a generic error at commit.
    private Product saveAndFlush(Product product, String sku) {
        try {
            return productRepository.saveAndFlush(product);
        } catch (DataIntegrityViolationException exception) {
            String cause = String.valueOf(exception.getMostSpecificCause().getMessage());
            if (cause.contains(SKU_UNIQUE_CONSTRAINT)) {
                throw new DuplicateSkuException(sku);
            }
            throw exception;
        }
    }

    private void applyRequest(Product product, ProductRequest request, String normalizedSku) {
        product.setSku(normalizedSku);
        product.setName(request.name());
        product.setDescription(request.description());
        product.setBrand(request.brand());
        product.setPrice(request.price());
        product.setStockQuantity(request.stockQuantity());
    }

    private String normalizeSku(String sku) {
        return sku.trim().toUpperCase(Locale.ROOT);
    }
}
