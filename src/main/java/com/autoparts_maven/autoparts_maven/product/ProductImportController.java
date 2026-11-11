package com.autoparts_maven.autoparts_maven.product;

import com.autoparts_maven.autoparts_maven.product.ProductImportService.ImportReport;
import java.io.IOException;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Admin-only (/api/admin/**): bulk create/update of products from a CSV spreadsheet. */
@RestController
@RequiredArgsConstructor
public class ProductImportController {

    private final ProductImportService productImportService;

    @PostMapping(value = "/api/admin/products/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportReport importProducts(@RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean dryRun) {
        if (file.isEmpty()) {
            throw new ProductImportException("The uploaded file is empty");
        }
        try (InputStream input = file.getInputStream()) {
            return productImportService.importCsv(input, dryRun);
        } catch (IOException exception) {
            throw new ProductImportException("Could not read the uploaded file");
        }
    }
}
