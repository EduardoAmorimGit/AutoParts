package com.autoparts_maven.autoparts_maven.product;

import com.autoparts_maven.autoparts_maven.common.PageResponse;
import com.autoparts_maven.autoparts_maven.product.dto.ProductRequest;
import com.autoparts_maven.autoparts_maven.product.dto.ProductResponse;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private static final String SKU_UNIQUE_CONSTRAINT = "uq_products_sku";

    private final ProductRepository productRepository;
    private final CategoryService categoryService;

    /**
     * Filters for the product listing. {@code includeInactive} must only be honoured for
     * administrators: customers never see inactive products.
     */
    public record ProductFilter(ProductType type, Long categoryId, String query, boolean includeInactive) {

        public static ProductFilter none() {
            return new ProductFilter(null, null, null, true);
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> findAll(Pageable pageable) {
        return findAll(ProductFilter.none(), pageable);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> findAll(ProductFilter filter, Pageable pageable) {
        return PageResponse.from(productRepository.findAll(matching(filter), pageable).map(ProductResponse::from));
    }

    @Transactional(readOnly = true)
    public ProductResponse findById(Long id) {
        return ProductResponse.from(getProductOrThrow(id));
    }

    /** Customer view: an inactive product is reported as not found. */
    @Transactional(readOnly = true)
    public ProductResponse findVisibleById(Long id, boolean includeInactive) {
        Product product = getProductOrThrow(id);
        if (!includeInactive && !Boolean.TRUE.equals(product.getActive())) {
            throw new ProductNotFoundException(id);
        }
        return ProductResponse.from(product);
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
        product.setType(request.type() == null ? ProductType.OTHER : request.type());

        return ProductResponse.from(saveAndFlush(product, sku));
    }

    /**
     * Full update of a product. {@code expectedVersion} (from an {@code If-Match} header) is optional;
     * when present it must match the stored version so a stale client cannot overwrite newer data.
     * An omitted {@code active} or {@code type} keeps the current value.
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
        if (request.type() != null) {
            product.setType(request.type());
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
        product.setCategory(request.categoryId() == null ? null : categoryService.getOrThrow(request.categoryId()));
        product.setViscosity(normalizeViscosity(request.viscosity()));
        product.setOilSpecification(blankToNull(request.oilSpecification()));
        product.setPackageVolumeLiters(request.packageVolumeLiters());
        product.getImageUrls().clear();
        if (request.imageUrls() != null) {
            product.getImageUrls().addAll(request.imageUrls().stream().map(String::trim).toList());
        }
    }

    static Specification<Product> matching(ProductFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!filter.includeInactive()) {
                predicates.add(cb.isTrue(root.get("active")));
            }
            if (filter.type() != null) {
                predicates.add(cb.equal(root.get("type"), filter.type()));
            }
            if (filter.categoryId() != null) {
                predicates.add(cb.equal(root.get("category").get("id"), filter.categoryId()));
            }
            if (filter.query() != null && !filter.query().isBlank()) {
                String like = "%" + filter.query().trim().toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), like, '\\'),
                        cb.like(cb.lower(root.get("sku")), like, '\\'),
                        cb.like(cb.lower(root.get("brand")), like, '\\')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    static String normalizeSku(String sku) {
        return sku.trim().toUpperCase(Locale.ROOT);
    }

    /** "5w-30" and "5W30" are the same viscosity grade. */
    static String normalizeViscosity(String viscosity) {
        String value = blankToNull(viscosity);
        return value == null ? null : value.toUpperCase(Locale.ROOT).replaceAll("[\\s-]", "");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
