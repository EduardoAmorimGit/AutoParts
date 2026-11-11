package com.autoparts_maven.autoparts_maven.product;

import com.autoparts_maven.autoparts_maven.product.dto.CategoryRequest;
import com.autoparts_maven.autoparts_maven.product.dto.CategoryResponse;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public List<CategoryResponse> findAll() {
        return categoryRepository.findAll(Sort.by("name")).stream().map(CategoryResponse::from).toList();
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        String name = request.name().trim();
        String slug = slugOf(name);
        if (categoryRepository.findBySlug(slug).isPresent()) {
            throw new DuplicateCategoryException(name);
        }
        Category category = new Category();
        category.setName(name);
        category.setSlug(slug);
        return CategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse rename(Long id, CategoryRequest request) {
        Category category = getOrThrow(id);
        String name = request.name().trim();
        String slug = slugOf(name);
        if (categoryRepository.existsBySlugAndIdNot(slug, id)) {
            throw new DuplicateCategoryException(name);
        }
        category.setName(name);
        category.setSlug(slug);
        return CategoryResponse.from(category);
    }

    /** Products of a deleted category stay, without category. */
    @Transactional
    public void delete(Long id) {
        Category category = getOrThrow(id);
        productRepository.clearCategory(id);
        categoryRepository.deleteById(category.getId());
    }

    /** Category with this name, created when it does not exist yet (used by spreadsheet imports). */
    @Transactional
    public Category findOrCreate(String name) {
        String trimmed = name.trim();
        return categoryRepository.findBySlug(slugOf(trimmed)).orElseGet(() -> {
            Category category = new Category();
            category.setName(trimmed);
            category.setSlug(slugOf(trimmed));
            return categoryRepository.save(category);
        });
    }

    Category getOrThrow(Long id) {
        return categoryRepository.findById(id).orElseThrow(() -> new CategoryNotFoundException(id));
    }

    /** "Filtros de Óleo" -> "filtros-de-oleo". */
    static String slugOf(String name) {
        String slug = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (slug.isEmpty()) {
            throw new InvalidCategoryNameException();
        }
        return slug;
    }
}
