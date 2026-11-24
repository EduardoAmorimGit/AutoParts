package com.autoparts_maven.autoparts_maven.legal;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Public: legal documents for the storefront and the version a checkout must accept. */
@RestController
@RequestMapping("/api/legal")
class LegalController {

    private final LegalDocuments documents;

    LegalController(LegalDocuments documents) {
        this.documents = documents;
    }

    @GetMapping("/documents")
    Map<String, Object> list() {
        List<LegalDocuments.Document> all = documents.all();
        return Map.of("version", documents.version(), "documents", all);
    }

    @GetMapping("/documents/{slug}")
    LegalDocuments.Document get(@PathVariable String slug) {
        return documents.get(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
    }
}
