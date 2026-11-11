package com.autoparts_maven.autoparts_maven.product;

import com.autoparts_maven.autoparts_maven.product.dto.CategoryRequest;
import com.autoparts_maven.autoparts_maven.product.dto.CategoryResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Public category list; changes are admin-only (/api/admin/**). */
@RestController
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping("/api/categories")
    public List<CategoryResponse> findAll() {
        return categoryService.findAll();
    }

    @PostMapping("/api/admin/categories")
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.create(request));
    }

    @PutMapping("/api/admin/categories/{id}")
    public CategoryResponse rename(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        return categoryService.rename(id, request);
    }

    @DeleteMapping("/api/admin/categories/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
